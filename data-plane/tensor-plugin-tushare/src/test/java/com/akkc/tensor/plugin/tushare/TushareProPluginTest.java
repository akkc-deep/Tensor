package com.akkc.tensor.plugin.tushare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class TushareProPluginTest {
    private static final String SECRET = "m07-t04-secret-sentinel";
    private static final List<String> API_NAMES = List.of(
            "adj_factor", "balancesheet", "block_trade", "broker_recommend", "cashflow", "daily",
            "daily_basic", "disclosure_date", "dividend", "express", "fina_audit", "fina_indicator",
            "fina_mainbz", "forecast", "hk_hold", "hs_const", "hsgt_top10", "income", "index_classify",
            "index_member", "index_member_all", "margin", "margin_detail", "moneyflow", "moneyflow_hsgt",
            "monthly", "namechange", "new_share", "pledge_detail", "pledge_stat", "repurchase", "share_float",
            "slb_len", "slb_sec", "slb_sec_detail", "stk_holdernumber", "stk_holdertrade", "stk_limit",
            "stk_managers", "stk_rewards", "stock_basic", "stock_company", "suspend_d", "top10_floatholders",
            "top10_holders", "top_inst", "top_list", "trade_cal", "weekly");

    @Test
    void exposesOnlyTheApprovedPluginConfigurationAndUnavailableFailureSurface() {
        assertThat(Modifier.isPublic(TushareProPlugin.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(TushareProPlugin.class.getModifiers())).isTrue();
        assertThat(TushareProPlugin.class.getInterfaces()).containsExactly(DataSourcePlugin.class);
        assertThat(TushareProPlugin.class.getConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        TushareProperties.class, TushareProClient.class, List.class));
        assertThat(publicDeclaredMethods(TushareProPlugin.class)).extracting(Method::getName)
                .containsExactlyInAnyOrder("descriptor", "readiness", "download");

        assertThat(Modifier.isPublic(TusharePluginConfiguration.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(TusharePluginConfiguration.class.getModifiers())).isTrue();
        Configuration configuration = TusharePluginConfiguration.class.getAnnotation(Configuration.class);
        assertThat(configuration).isNotNull();
        assertThat(configuration.proxyBeanMethods()).isFalse();
        assertThat(TusharePluginConfiguration.class.getAnnotation(EnableConfigurationProperties.class).value())
                .containsExactly(TushareProperties.class);
        assertThat(TusharePluginConfiguration.class.getConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isZero());
        assertThat(publicDeclaredMethods(TusharePluginConfiguration.class)).singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("tushareProPlugin");
                    assertThat(method.getParameterTypes()).containsExactly(TushareProperties.class);
                    assertThat(method.getReturnType()).isEqualTo(TushareProPlugin.class);
                    assertThat(method.getAnnotation(Bean.class)).isNotNull();
                });

        assertThat(TushareProPlugin.class.getDeclaredClasses()).singleElement().satisfies(type -> {
            int modifiers = type.getModifiers();
            assertThat(Modifier.isPrivate(modifiers)).isTrue();
            assertThat(Modifier.isStatic(modifiers)).isTrue();
            assertThat(Modifier.isFinal(modifiers)).isTrue();
            assertThat(type.getSuperclass()).isEqualTo(TensorException.class);
            assertThat(type.getDeclaredConstructors()).singleElement()
                    .satisfies(constructor -> {
                        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
                        assertThat(constructor.getParameterCount()).isZero();
                    });
            assertThat(publicDeclaredMethods(type)).isEmpty();
        });
    }

    @Test
    void projectsAllDefinitionMetadataIntoTheFixedReadyDescriptor() {
        List<DatasetDefinition> definitions = definitions();
        TushareProPlugin plugin = plugin(properties(true, SECRET), mock(TushareProClient.class), definitions);

        PluginDescriptor descriptor = plugin.descriptor();
        PluginReadiness readiness = plugin.readiness();
        assertThat(descriptor.pluginId().value()).isEqualTo("tushare_pro");
        assertThat(descriptor.displayName()).isEqualTo("Tushare Pro");
        assertThat(descriptor.description()).isEqualTo("Tushare Pro 证券数据源");
        assertDescriptorReadiness(descriptor, readiness);
        assertThat(descriptor.apis()).hasSize(49);
        assertThat(descriptor.datasets()).hasSize(49);

        for (int index = 0; index < definitions.size(); index++) {
            DatasetDefinition definition = definitions.get(index);
            ApiDescriptor api = descriptor.apis().get(index);
            assertThat(api.apiName()).isEqualTo(definition.datasetKey().apiName());
            assertThat(api.displayName()).isEqualTo(definition.displayName());
            assertThat(api.category()).isEqualTo(definition.category());
            assertThat(api.queryMode()).isEqualTo(definition.queryMode());
            assertThat(api.parameters()).isEqualTo(definition.parameters());
            assertThat(descriptor.datasets().get(index)).isEqualTo(definition.datasetKey());
        }
        assertThat(!String.valueOf(descriptor).contains(SECRET)).as("descriptor omits credentials").isTrue();
    }

    @Test
    void requiresExactlyTheIndependentOrderedSetOfFortyNineApis() {
        List<DatasetDefinition> definitions = definitions();
        TushareProPlugin plugin = plugin(properties(true, SECRET), mock(TushareProClient.class), definitions);

        assertThat(plugin.descriptor().apis()).extracting(api -> api.apiName().value())
                .containsExactlyElementsOf(API_NAMES);
        assertThat(plugin.descriptor().datasets()).extracting(key -> key.apiName().value())
                .containsExactlyElementsOf(API_NAMES);
        assertThatThrownBy(() -> plugin(properties(true, SECRET), mock(TushareProClient.class),
                definitions.subList(0, 48)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("definitions must contain exactly 49 datasets");
    }

    @Test
    void keepsDescriptorAndReadinessAlignedForEveryLocalConfigurationState() {
        List<TushareProperties> states = List.of(
                properties(false, ""),
                properties(false, SECRET),
                properties(true, ""),
                properties(true, "   "),
                properties(true, SECRET));

        for (TushareProperties state : states) {
            TushareProPlugin plugin = plugin(state, mock(TushareProClient.class), definitions());
            assertDescriptorReadiness(plugin.descriptor(), state.readiness());
            assertThat(plugin.readiness()).isEqualTo(state.readiness());
            assertThat(!String.valueOf(plugin.descriptor()).contains(SECRET)
                    && !String.valueOf(plugin.readiness()).contains(SECRET)
                    && !String.valueOf(state).contains(SECRET))
                    .as("configuration surfaces omit credentials")
                    .isTrue();
        }
    }

    @Test
    void createsOnePluginBeanWithAllMetadataWhenMissingCredentialsOrDisabled() {
        assertLocalContext(Map.of("tensor.plugins.tushare-pro.base-url", "https://m07-t04.invalid"),
                true, false, "Credentials missing");
        assertLocalContext(Map.of(
                "tensor.plugins.tushare-pro.enabled", "false",
                "tensor.plugins.tushare-pro.base-url", "https://m07-t04.invalid",
                "tensor.plugins.tushare-pro.token", SECRET), false, true, "Disabled");
    }

    @Test
    void rejectsUnavailableDownloadsBeforeAnyClientInteraction() {
        for (TushareProperties state : List.of(
                properties(false, ""), properties(false, SECRET), properties(true, ""))) {
            TushareProClient client = mock(TushareProClient.class);
            TushareProPlugin plugin = plugin(state, client, definitions());

            Throwable failure = catchThrowable(() -> plugin.download(ApiName.of("daily"), Map.of()));

            assertThat(failure).isInstanceOf(TensorException.class);
            TensorException unavailable = (TensorException) failure;
            assertThat(unavailable.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
            assertThat(unavailable.getMessage()).isEqualTo("Tushare Pro download is unavailable");
            assertThat(unavailable.retryable()).isFalse();
            assertThat(unavailable.getCause()).isNull();
            assertThat(unavailable.getSuppressed()).isEmpty();
            verifyNoInteractions(client);
        }
    }

    @Test
    void rejectsNullAndUnknownInputsInTheApprovedOrderWithoutCallingTheClient() {
        TushareProClient readyClient = mock(TushareProClient.class);
        TushareProPlugin ready = plugin(properties(true, SECRET), readyClient, definitions());

        assertThatThrownBy(() -> ready.download(null, Map.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("apiName");
        assertThatThrownBy(() -> ready.download(ApiName.of("daily"), null))
                .isInstanceOf(NullPointerException.class).hasMessage("params");
        Throwable unknown = catchThrowable(() -> ready.download(ApiName.of("unknown_api"), Map.of()));
        assertThat(unknown).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown Tushare API");
        assertThat(!String.valueOf(unknown).contains("unknown_api"))
                .as("unknown API failure omits caller input").isTrue();
        verifyNoInteractions(readyClient);

        TushareProClient disabledClient = mock(TushareProClient.class);
        TushareProPlugin disabled = plugin(properties(false, SECRET), disabledClient, definitions());
        Throwable disabledUnknown = catchThrowable(
                () -> disabled.download(ApiName.of("unknown_api"), Map.of()));
        assertThat(disabledUnknown).isInstanceOf(TensorException.class);
        assertThat(((TensorException) disabledUnknown).code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
        verifyNoInteractions(disabledClient);
    }

    @Test
    void delegatesTheExactDailyCallOnceAndPreservesResultAndSourceFailureIdentity() {
        List<DatasetDefinition> definitions = definitions();
        DatasetDefinition daily = definitions.stream()
                .filter(definition -> definition.datasetKey().apiName().value().equals("daily"))
                .findFirst().orElseThrow();
        Map<String, Object> params = Map.of("trade_date", "20260903");
        DownloadEnvelope envelope = mock(DownloadEnvelope.class);
        TushareProClient successClient = mock(TushareProClient.class);
        when(successClient.execute(same(daily), same(params))).thenReturn(envelope);

        DownloadEnvelope result = plugin(properties(true, SECRET), successClient, definitions)
                .download(ApiName.of("daily"), params);

        assertThat(result).isSameAs(envelope);
        verify(successClient).execute(same(daily), same(params));
        verifyNoMoreInteractions(successClient);

        SourceException sourceFailure = new SourceException(
                ErrorCode.SOURCE_TIMEOUT, "Tushare response timed out");
        TushareProClient failureClient = mock(TushareProClient.class);
        when(failureClient.execute(same(daily), same(params))).thenThrow(sourceFailure);

        assertThatThrownBy(() -> plugin(properties(true, SECRET), failureClient, definitions)
                .download(ApiName.of("daily"), params)).isSameAs(sourceFailure);
        verify(failureClient).execute(same(daily), same(params));
        verifyNoMoreInteractions(failureClient);
    }

    private static TushareProPlugin plugin(
            TushareProperties properties, TushareProClient client, List<DatasetDefinition> definitions) {
        return new TushareProPlugin(properties, client, definitions);
    }

    private static TushareProperties properties(boolean enabled, String token) {
        return new TushareProperties(
                enabled,
                URI.create("https://m07-t04.invalid"),
                new TushareProperties.Credential(token),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                1_024);
    }

    private static List<DatasetDefinition> definitions() {
        return new DatasetDefinitionLoader().loadAll(
                new PathMatchingResourcePatternResolver(),
                "classpath*:datasets/tushare_pro/*.yaml");
    }

    private static Method[] publicDeclaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                .toArray(Method[]::new);
    }

    private static void assertDescriptorReadiness(PluginDescriptor descriptor, PluginReadiness readiness) {
        assertThat(descriptor.enabled()).isEqualTo(readiness.enabled());
        assertThat(descriptor.credentialConfigured()).isEqualTo(readiness.credentialConfigured());
        assertThat(descriptor.downloadAvailable()).isEqualTo(readiness.downloadAvailable());
        assertThat(descriptor.unavailableReason()).isEqualTo(readiness.unavailableReason());
    }

    private static void assertLocalContext(
            Map<String, Object> properties,
            boolean enabled,
            boolean credentialConfigured,
            String unavailableReason) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("m07-t04", properties));
            context.register(TusharePluginConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(TushareProperties.class)).hasSize(1);
            assertThat(context.getBeansOfType(DataSourcePlugin.class)).hasSize(1);
            assertThat(context.getBeansOfType(TushareProPlugin.class)).hasSize(1);
            TushareProPlugin plugin = context.getBean(TushareProPlugin.class);
            assertThat(plugin.descriptor().apis()).hasSize(49);
            assertThat(plugin.descriptor().datasets()).hasSize(49);
            assertThat(plugin.readiness()).isEqualTo(
                    new PluginReadiness(enabled, credentialConfigured, false, unavailableReason));
        }
    }
}
