package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class TushareRestClientFactory {
    static final String CONTROL_ATTRIBUTE = "tensor.tushare.requestControl";

    private final HttpClient httpClient;

    public TushareRestClientFactory() {
        this.httpClient = null;
    }

    TushareRestClientFactory(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public RestClient create(TushareProperties properties) {
        Objects.requireNonNull(properties, "properties");
        HttpClient sharedHttpClient = httpClient == null ? createHttpClient(properties.connectTimeout()) : httpClient;
        ClientHttpRequestFactory requestFactory = (uri, method) ->
                new ControlledRequest(sharedHttpClient, uri, method, properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", "Tensor/1.0")
                .requestFactory(requestFactory)
                .build();
    }

    static HttpClient createHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    record RequestControl(BatchCallContext context, Clock clock) {
        RequestControl {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(clock, "clock");
        }
    }

    private static final class ControlledRequest extends AbstractClientHttpRequest {
        private static final Duration ONE_MILLISECOND = Duration.ofMillis(1);

        private final HttpClient httpClient;
        private final URI uri;
        private final HttpMethod method;
        private final Duration configuredTimeout;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private ControlledRequest(HttpClient httpClient, URI uri, HttpMethod method, Duration configuredTimeout) {
            this.httpClient = httpClient;
            this.uri = uri;
            this.method = method;
            this.configuredTimeout = configuredTimeout;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        protected OutputStream getBodyInternal(HttpHeaders headers) {
            return body;
        }

        @Override
        protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
            Object attribute = getAttributes().get(CONTROL_ATTRIBUTE);
            RequestControl control = attribute == null ? null : (RequestControl) attribute;
            TimeoutSelection timeout = timeout(control);
            JdkClientHttpRequestFactory localFactory =
                    new JdkClientHttpRequestFactory(new TimeoutHttpClient(httpClient, timeout.duration()));
            localFactory.setReadTimeout(timeout.duration());
            ClientHttpRequest delegate = localFactory.createRequest(uri, method);
            delegate.getHeaders().putAll(headers);
            if (body.size() > 0) {
                body.writeTo(delegate.getBody());
            }
            if (control != null) {
                TushareRequestGate.check(control.context(), control.clock());
                if (remaining(control).compareTo(ONE_MILLISECOND) < 0) {
                    throw TushareRequestGate.failure(ErrorCode.TASK_LIMIT_EXCEEDED);
                }
            }
            long startedNanos = System.nanoTime();
            try {
                ClientHttpResponse response = delegate.execute();
                return control == null ? response : new TimedResponse(response, timeout, startedNanos);
            } catch (IOException failure) {
                if (control != null && timeout.expired(startedNanos)) {
                    throw timeout.failure();
                }
                throw failure;
            }
        }

        private TimeoutSelection timeout(RequestControl control) {
            if (control != null) {
                TushareRequestGate.check(control.context(), control.clock());
                Duration remaining = remaining(control);
                if (remaining.compareTo(ONE_MILLISECOND) < 0) {
                    throw TushareRequestGate.failure(ErrorCode.TASK_LIMIT_EXCEEDED);
                }
                if (remaining.compareTo(configuredTimeout) < 0) {
                    return new TimeoutSelection(Duration.ofMillis(remaining.toMillis()),
                            ErrorCode.TASK_LIMIT_EXCEEDED);
                }
            }
            if (configuredTimeout.compareTo(ONE_MILLISECOND) < 0) {
                throw TushareErrorClassifier.failure(ErrorCode.SOURCE_TIMEOUT);
            }
            return new TimeoutSelection(Duration.ofMillis(configuredTimeout.toMillis()), ErrorCode.SOURCE_TIMEOUT);
        }

        private Duration remaining(RequestControl control) {
            return Duration.between(control.clock().instant(), control.context().deadline());
        }
    }

    private record TimeoutSelection(Duration duration, ErrorCode failureCode) {
        boolean expired(long startedNanos) {
            return System.nanoTime() - startedNanos >= duration.toNanos();
        }

        RuntimeException failure() {
            return failureCode == ErrorCode.TASK_LIMIT_EXCEEDED
                    ? TushareRequestGate.failure(failureCode)
                    : TushareErrorClassifier.failure(failureCode);
        }
    }

    private static final class TimedResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final TimeoutSelection timeout;
        private final long startedNanos;

        private TimedResponse(ClientHttpResponse delegate, TimeoutSelection timeout, long startedNanos) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.startedNanos = startedNanos;
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            try {
                return delegate.getStatusCode();
            } catch (IOException failure) {
                throw afterTimeout(failure);
            }
        }

        @Override
        public String getStatusText() throws IOException {
            try {
                return delegate.getStatusText();
            } catch (IOException failure) {
                throw afterTimeout(failure);
            }
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            try {
                return new TimedInputStream(delegate.getBody(), this);
            } catch (IOException failure) {
                throw afterTimeout(failure);
            }
        }

        @Override
        public void close() {
            RuntimeException closeFailure = null;
            try {
                delegate.close();
            } catch (RuntimeException failure) {
                closeFailure = failure;
            }
            if (timeout.expired(startedNanos)) {
                throw timeout.failure();
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

        private IOException afterTimeout(IOException failure) {
            if (timeout.expired(startedNanos)) {
                throw timeout.failure();
            }
            return failure;
        }

        private void checkTimeout() {
            if (timeout.expired(startedNanos)) {
                throw timeout.failure();
            }
        }
    }

    private static final class TimedInputStream extends FilterInputStream {
        private final TimedResponse response;

        private TimedInputStream(InputStream input, TimedResponse response) {
            super(input);
            this.response = response;
        }

        @Override
        public int read() throws IOException {
            try {
                int value = super.read();
                response.checkTimeout();
                return value;
            } catch (IOException failure) {
                throw response.afterTimeout(failure);
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            try {
                int count = super.read(bytes, offset, length);
                response.checkTimeout();
                return count;
            } catch (IOException failure) {
                throw response.afterTimeout(failure);
            }
        }
    }

    /** Adds the timeout to the immutable JDK request while delegating all transport work to the shared client. */
    private static final class TimeoutHttpClient extends HttpClient {
        private final HttpClient delegate;
        private final Duration timeout;

        private TimeoutHttpClient(HttpClient delegate, Duration timeout) {
            this.delegate = delegate;
            this.timeout = timeout;
        }

        private HttpRequest withTimeout(HttpRequest request) {
            return HttpRequest.newBuilder(request, (name, value) -> true).timeout(timeout).build();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override public Redirect followRedirects() { return delegate.followRedirects(); }
        @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
        @Override public SSLContext sslContext() { return delegate.sslContext(); }
        @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
        @Override public Version version() { return delegate.version(); }
        @Override public Optional<Executor> executor() { return delegate.executor(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            return delegate.send(withTimeout(request), responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return delegate.sendAsync(withTimeout(request), responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return delegate.sendAsync(withTimeout(request), responseBodyHandler, pushPromiseHandler);
        }
    }
}
