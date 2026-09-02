package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class TushareRestClientFactory {
    public RestClient create(TushareProperties properties) {
        Objects.requireNonNull(properties, "properties");
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(createHttpClient(properties.connectTimeout()));
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", "Tensor/1.0")
                .requestFactory(requestFactory)
                .build();
    }

    static HttpClient createHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }
}
