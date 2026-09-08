package com.akkc.tensor.plugin.tushare;

import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.client.TushareRestClientFactory;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.List;
import java.util.Map;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.tushare.metadata.DownloadPolicyLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TushareProperties.class)
public final class TusharePluginConfiguration {
    @Bean("tushareDatasetDefinitions")
    public List<DatasetDefinition> tushareDatasetDefinitions() {
        return new DatasetDefinitionLoader().loadAll(
                new PathMatchingResourcePatternResolver(),
                "classpath*:datasets/tushare_pro/*.yaml");
    }

    @Bean("tushareDownloadPolicies")
    public Map<ApiName, DownloadPolicy> tushareDownloadPolicies() {
        return new DownloadPolicyLoader().load(new ClassPathResource("download/tushare-pro-policies.yaml"));
    }

    @Bean
    public TushareProPlugin tushareProPlugin(
            TushareProperties properties,
            @Qualifier("tushareDatasetDefinitions")
                    List<DatasetDefinition> definitions,
            @Qualifier("tushareDownloadPolicies") Map<ApiName, DownloadPolicy> policies) {
        TushareProClient client = new TushareProClient(
                new TushareRestClientFactory().create(properties), properties);
        return new TushareProPlugin(properties, client, definitions, policies);
    }
}
