package com.akkc.tensor.plugin.tushare.config;

import com.akkc.tensor.plugin.api.constant.StringConstants;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.tushare.TushareConstants;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tensor.plugins.tushare-pro")
public record TushareProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.tushare.pro") URI baseUrl,
        @DefaultValue("") Credential token,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("120s") Duration readTimeout,
        @DefaultValue("67108864") int maxResponseBytes,
        @DefaultValue("1500ms") Duration minRequestInterval) {
    private static final String DISABLED_REASON = "Disabled";
    private static final String MISSING_CREDENTIALS_REASON = "Credentials missing";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String REDACTED_TEXT = "[REDACTED]";

    @ConstructorBinding
    public TushareProperties {
        token = token == null ? new Credential(StringConstants.EMPTY) : token;
        if (!validBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP(S) URI without credentials, query, or fragment");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()
                || readTimeout.compareTo(Duration.ofSeconds(TushareConstants.MAX_READ_TIMEOUT_SECONDS)) > 0) {
            throw new IllegalArgumentException("readTimeout must be positive and at most 120 seconds");
        }
        if (maxResponseBytes < 1 || maxResponseBytes > TushareConstants.MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("maxResponseBytes must be between 1 and 67108864");
        }
        if (minRequestInterval == null || minRequestInterval.isNegative()) {
            throw new IllegalArgumentException("minRequestInterval must be non-negative and fit in nanoseconds");
        }
        try {
            minRequestInterval.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("minRequestInterval must be non-negative and fit in nanoseconds");
        }
    }

    public TushareProperties(boolean enabled, URI baseUrl, Credential token, Duration connectTimeout,
                             Duration readTimeout, int maxResponseBytes) {
        this(enabled, baseUrl, token, connectTimeout, readTimeout, maxResponseBytes,
                Duration.ofMillis(TushareConstants.DEFAULT_MIN_REQUEST_INTERVAL_MILLIS));
    }

    public PluginReadiness readiness() {
        boolean configured = token.configured();
        if (!enabled) {
            return new PluginReadiness(false, configured, false, DISABLED_REASON);
        }
        if (!configured) {
            return new PluginReadiness(true, false, false, MISSING_CREDENTIALS_REASON);
        }
        return new PluginReadiness(true, true, true, null);
    }

    private static boolean validBaseUrl(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null) {
            return false;
        }
        return HTTP_SCHEME.equalsIgnoreCase(value.getScheme())
                || HTTPS_SCHEME.equalsIgnoreCase(value.getScheme());
    }

    public record Credential(String value) {
        public Credential {
            value = value == null ? StringConstants.EMPTY : value;
        }

        public boolean configured() {
            return !value.isBlank();
        }

        @Override
        public String toString() {
            return REDACTED_TEXT;
        }
    }
}
