# M09-T06 Safe Configuration and Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete Tensor Servlet production bean graph and add environment-backed secrets, one safe completion event per download/query, the five frozen low-cardinality metrics, MySQL health behavior, and secure static/API response defaults.

**Architecture:** A Servlet-only `ApplicationConfiguration` composes existing plugin, adapter, catalog, JDBC, transaction, download, query, and observability objects after Flyway initialization. `OperationLogger` explicitly wraps the two Controller operations and delegates bounded measurements to `TensorMetrics`; neither component reads parameter values or changes business results. Boot's database health contributor remains the only external health probe, while one ordered Servlet filter applies security and cache headers to every response.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Framework 6.2.19, Micrometer 1.15.12, Actuator, Spring JDBC, Flyway, SLF4J/Logback, JUnit Jupiter 5.12.2, AssertJ, Spring Mock Servlet, Testcontainers MySQL 8.4.6.

**Spec:** `docs/task-designs/M09-T06-design.md`

## Global Constraints

- The authoritative design is `docs/task-designs/M09-T06-design.md`; its 13-file implementation scope, interfaces, labels, header values, test counts, and exclusions are mandatory.
- Do not modify either POM, Core, plugin-api, fixture, OpenAPI, error codes, migrations, DTOs, `TensorApplication`, `RequestIdFilter`, `GlobalExceptionHandler`, or `JacksonPrecisionConfiguration`.
- Keep `ApplicationConfiguration`, observability beans, and the security filter Servlet-only so the existing non-Web smoke context never requires database auto-configuration.
- Load the 49 Tushare definitions once through the named `tushareDatasetDefinitions` Bean; the plugin and generic adapters must consume those definitions rather than load YAML twice.
- Build `DatasetCatalog` after Flyway; only adapters whose definitions survive catalog/schema validation may enter `AdapterRegistry`.
- Do not move transaction boundaries, add retries, contact Tushare during startup/health, or create plugin/API-specific business branches.
- Metric names must be the five exact TRD 17.3 names; tags are limited to `plugin`, `api`, `outcome`, and rows-only `kind`, with the frozen outcome/kind values.
- Never log parameter/filter values, Token, Authorization, Cookie, database password, JDBC URL, exception message/cause/Throwable, SQL, raw upstream bodies, stack traces, or internal paths from `OperationLogger`.
- Unknown client-supplied plugin/API values must not create a metric or operation-completion event. The existing global handler remains responsible for its safe rejection log.
- Default Actuator HTTP exposure is health only; env, configprops, metrics, prometheus, heapdump, loggers, and beans remain unexposed.
- Use strict TDD and preserve the frozen counts: baseline 320, `ObservabilityTest` 18, affected regression 51, production context 1, schema/context pair 53, final default reactor 338.
- The single implementation commit must contain exactly the design's 13 files and use `feat(app): add safe configuration and observability`.

---

### Task 1: Implement M09-T06 configuration, production wiring, observability, health, and response security

**Files:**
- Create: `data-plane/tensor-app/src/main/resources/application.yml`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/TensorMetrics.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/OperationLogger.java`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ObservabilityTest.java`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ProductionApplicationContextIT.java`
- Modify: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java`
- Modify: `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java`
- Modify: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`
- Modify: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java`
- Modify: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`
- Modify: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java`

**Interfaces:**
- Consumes: the current public constructors of `PluginRegistry`, `AdapterRegistry`, `DatasetStartupValidator`, `SchemaInspector`, `GenericDatasetAdapter`, JDBC repositories/services, `DownloadService`, `DatasetQueryService`, `TushareProPlugin`, and the existing Controller/DTO/error/request-ID contracts.
- Produces: named `List<DatasetDefinition> tushareDatasetDefinitions`; named `List<DatasetAdapter> tensorDatasetAdapters`; one Servlet production Bean for each current registry/catalog/repository/service; `TensorMetrics(MeterRegistry, PluginRegistry)`; `OperationLogger(PluginRegistry, TensorMetrics)`; the `OperationLogger.download` and `OperationLogger.query` wrappers; one ordered response-security filter; safe `application.yml` defaults.

- [ ] **Step 1: Re-read all authoritative inputs and verify the clean 320-test baseline**

Read in this exact order:

```text
docs/task-designs/M09-T06-design.md
docs/superpowers/plans/2026-09-04-m09-t06-safe-configuration-observability.md
docs/task-handoffs/M09-T06-handoff.md
docs/task-handoffs/tensor-v1-task-board.md (M09-T06 row and detail)
docs/superpowers/plans/tensor-modules/M09-app-api.md (Global Constraints, Task M09-T06, Module Gate)
docs/design/Tensor_多源证券数据平台_TRD_v1.0.md (6, 7.2, 14-17, Appendix B)
docs/design/Tensor_多源证券数据平台_PRD_v1.0.md (3.3, 7.5, 9, 10.3, 10.6, 12.1)
docs/task-designs/M09-T01-design.md
docs/task-designs/M09-T02-design.md
docs/task-designs/M09-T03-design.md
docs/task-designs/M09-T04-design.md
docs/task-designs/M09-T05-design.md
data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java
data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java
```

Run in an environment that permits Mockito/Byte Buddy self-attach:

```bash
git status --short
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

Expected: Git status is empty; plugin-api 79, core 75, Tushare 93, fixture 12, app 61, total 320/320; zero failures/errors/skips; all six Enforcer executions, app ArchUnit, and forbidden-Git tests pass. Stop if the baseline differs.

- [ ] **Step 2: Write the complete ordinary and production-context test contracts before production code**

Create `ObservabilityTest` in package `com.akkc.tensor.observability`. It uses these fixed values and clears MDC after every invocation:

```java
private static final String REQUEST_ID =
        "89a09af7-e54b-440b-9e46-ff7aa2184b1a";
private static final String SECRET = "m09-t06-token-password-secret";
private static final DatasetKey KNOWN =
        DatasetKey.of(PluginId.of("test_plugin"), ApiName.of("daily"));

@AfterEach
void clearMdc() {
    MDC.clear();
}
```

Build the test registry without Mockito. The test-local `DataSourcePlugin` returns one descriptor whose API is `daily`, whose declared parameters are `trade_date` then `ts_code`, and whose datasets list contains `KNOWN`; its `download` method throws `UnsupportedOperationException` because the logger tests never call the plugin. Construct the subject exactly as follows:

```java
private static Subjects subjects() {
    PluginRegistry plugins = new PluginRegistry(List.of(new TestPlugin()));
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    TensorMetrics metrics = new TensorMetrics(registry, plugins);
    return new Subjects(registry, metrics, new OperationLogger(plugins, metrics));
}

private record Subjects(
        SimpleMeterRegistry registry,
        TensorMetrics metrics,
        OperationLogger logger) {
}
```

Add exactly these 11 test methods; their parameter sources produce 18 Surefire invocations:

```java
@Test void recordsDownloadSuccessOnce();
@Test void recordsDownloadEmptyOnce();
@ParameterizedTest
@MethodSource("downloadFailures")
void classifiesDownloadFailureWithoutReplacingIt(
        RuntimeException failure, ErrorCode code, String stage);
@Test void recordsQuerySuccessOnce();
@Test void classifiesQueryFailureWithoutReplacingIt();
@Test void removesSecretsValuesAndThrowableTextFromCompletionEvents();
@Test void skipsUnknownKeysWithoutSkippingTheOperation();
@Test void exposesOnlyTheFrozenMeterSchema();
@ParameterizedTest
@ValueSource(strings = {"/api/v1/downloads", "/assets/app-deadbeef.js"})
void writesAllSecurityHeaders(String path);
@ParameterizedTest
@CsvSource({
        "/index.html, no-store",
        "/assets/app-deadbeef.js, 'public, max-age=31536000, immutable'"
})
void appliesStaticCachePolicy(String path, String expected);
@Test void loadsOnlyTheApprovedEnvironmentAndActuatorDefaults();
```

`downloadFailures()` returns exactly these six rows:

```java
private static Stream<Arguments> downloadFailures() {
    return Stream.of(
            Arguments.of(new TestTensorException(ErrorCode.PARAM_INVALID),
                    ErrorCode.PARAM_INVALID, "parameter"),
            Arguments.of(new TestTensorException(ErrorCode.PLUGIN_DISABLED),
                    ErrorCode.PLUGIN_DISABLED, "registration"),
            Arguments.of(new TestTensorException(ErrorCode.SOURCE_TIMEOUT),
                    ErrorCode.SOURCE_TIMEOUT, "source"),
            Arguments.of(new TestTensorException(ErrorCode.ADAPTER_TYPE_INVALID),
                    ErrorCode.ADAPTER_TYPE_INVALID, "adapter"),
            Arguments.of(new DataAccessResourceFailureException(SECRET),
                    ErrorCode.PERSISTENCE_FAILED, "persistence"),
            Arguments.of(new IllegalStateException(SECRET),
                    ErrorCode.INTERNAL_ERROR, "internal"));
}
```

Every logger test sets `MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID)`, attaches a `ListAppender<ILoggingEvent>` only to `LoggerFactory.getLogger(OperationLogger.class)`, and detaches/stops it in `finally`. Use these exact successful DTOs:

```java
DownloadResponse success = new DownloadResponse(
        REQUEST_ID, DownloadOutcome.SUCCESS, "test_plugin", "daily",
        5, 2, 3, "下载成功");
DownloadResponse empty = new DownloadResponse(
        REQUEST_ID, DownloadOutcome.EMPTY, "test_plugin", "daily",
        0, 0, 0, "下载成功，0 条数据");
PageResponse page = new PageResponse(
        REQUEST_ID, "test_plugin", "daily", 1, 50, 2, 1,
        List.of("ts_code"),
        List.of(Map.of("ts_code", "000001.SZ"), Map.of("ts_code", "000002.SZ")));
```

Assert meter values by exact name and tags. For example, download success must satisfy:

```java
assertThat(subjects.registry().get("tensor_download_total")
        .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
        .counter().count()).isEqualTo(1.0);
assertThat(subjects.registry().get("tensor_download_duration_seconds")
        .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
        .timer().count()).isEqualTo(1L);
assertThat(subjects.registry().get("tensor_download_rows_total")
        .tags("plugin", "test_plugin", "api", "daily", "kind", "source")
        .counter().count()).isEqualTo(5.0);
assertThat(subjects.registry().get("tensor_download_rows_total")
        .tags("plugin", "test_plugin", "api", "daily", "kind", "inserted")
        .counter().count()).isEqualTo(2.0);
assertThat(subjects.registry().get("tensor_download_rows_total")
        .tags("plugin", "test_plugin", "api", "daily", "kind", "updated")
        .counter().count()).isEqualTo(3.0);
```

For all success/failure calls, assert exactly one formatted message begins `tensor.operation.completed`, contains the fixed operation fields, and has no Throwable proxy. Failure cases use AssertJ to verify the thrown instance `isSameAs(failure)`. The redaction test passes this map and makes the supplier throw a `TestTensorException` whose message and cause both contain `SECRET`:

```java
Map<String, Object> unsafe = new LinkedHashMap<>();
unsafe.put("trade_date", SECRET);
unsafe.put("ts_code", SECRET);
unsafe.put("token", SECRET);
unsafe.put("Authorization", SECRET);
unsafe.put("Cookie", SECRET);
unsafe.put("db_password", SECRET);
unsafe.put("credential", SECRET);
```

Assert the log includes `paramSummary=[trade_date, ts_code]` but contains neither `SECRET` nor any rejected key. The meter-schema test iterates `registry.getMeters()` and accepts only the five names, the designed meter types, exact tag-key sets, supported plugin/API, and the frozen outcome/kind values.

For security tests, instantiate `new WebSecurityHeadersConfiguration().securityHeadersFilter().getFilter()`, call it with `MockHttpServletRequest`, `MockHttpServletResponse`, and a no-op chain, then assert the six exact header strings and cache values from the design.

Load YAML without Boot Test:

```java
PropertySource<?> source = new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .getFirst();
```

Assert the seven exact environment placeholders beginning `${TENSOR_`, `5s`, `120s`, `67108864`, `500`, `50`, `[20, 50, 100]`, `/actuator`, disabled discovery, exposure `health`, probes enabled, show-components `always`, show-details `never`, and both show-values settings `never`. Concatenated property values must not contain `SECRET`.

Create `ProductionApplicationContextIT` with one ordinary `@Test`, one manually managed container created as `new MySQLContainer<>("mysql:8.4.6").withDatabaseName("tensor").withUsername("tensor").withPassword(SECRET)`, `SpringApplicationBuilder(TensorApplication.class).web(WebApplicationType.SERVLET)`, and `server.port=0`. Initially keep its test body complete but let its private `start(MySQLContainer<?>, String)` helper throw only:

```java
throw new UnsupportedOperationException("Production application context not wired");
```

The final body must already call `start(mysql, "")`, assert missing-Token health and the production Bean graph, close it, call `start(mysql, SECRET)`, assert secret-free logs/responses and hidden endpoints, stop MySQL, then assert health returns 503/DOWN. Do not add Boot Test, Awaitility, H2, or a second test method.

- [ ] **Step 3: Run the test-only state and verify the strict RED**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=ObservabilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-zero at `tensor-app:testCompile`, caused only by missing `ApplicationConfiguration`, `TensorMetrics`, `OperationLogger`, and `WebSecurityHeadersConfiguration`. Fix test imports/signatures or use only already-present dependencies if any other error appears. Save complete output to `/private/tmp/m09-t06-red.log`; never add it to Git.

- [ ] **Step 4: Add environment-backed application and Tushare definition configuration**

Create `application.yml` with this exact content:

```yaml
spring:
  datasource:
    url: ${TENSOR_DB_URL}
    username: ${TENSOR_DB_USERNAME}
    password: ${TENSOR_DB_PASSWORD}
  flyway:
    enabled: true

tensor:
  display-zone: ${TENSOR_DISPLAY_ZONE:Asia/Shanghai}
  plugins:
    tushare-pro:
      enabled: ${TENSOR_TUSHARE_ENABLED:true}
      base-url: ${TENSOR_TUSHARE_BASE_URL:https://api.tushare.pro}
      token: ${TENSOR_TUSHARE_TOKEN:}
      connect-timeout: 5s
      read-timeout: 120s
      max-response-bytes: 67108864
  persistence:
    batch-size: 500
  query:
    default-page-size: 50
    allowed-page-sizes: [20, 50, 100]

management:
  endpoints:
    web:
      base-path: /actuator
      discovery:
        enabled: false
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-components: always
      show-details: never
    env:
      show-values: never
    configprops:
      show-values: never
  health:
    db:
      enabled: true
```

Replace `TusharePluginConfiguration` with this two-Bean shape, retaining its current annotations:

```java
@Bean("tushareDatasetDefinitions")
public List<DatasetDefinition> tushareDatasetDefinitions() {
    return new DatasetDefinitionLoader().loadAll(
            new PathMatchingResourcePatternResolver(),
            "classpath*:datasets/tushare_pro/*.yaml");
}

@Bean
public TushareProPlugin tushareProPlugin(
        TushareProperties properties,
        @Qualifier("tushareDatasetDefinitions")
                List<DatasetDefinition> definitions) {
    TushareProClient client = new TushareProClient(
            new TushareRestClientFactory().create(properties), properties);
    return new TushareProPlugin(properties, client, definitions);
}
```

Add imports for `DatasetDefinition`, `List`, and `Qualifier`; remove the loader call from `tushareProPlugin`. Update `TushareProPluginTest` so the public-method assertion accepts exactly `tushareDatasetDefinitions` and `tushareProPlugin`, verifies the first is a zero-argument `@Bean("tushareDatasetDefinitions")` returning `List`, and verifies the second takes `TushareProperties,List`. In `assertLocalContext`, obtain the named list and assert size 49 and that its ordered API names equal both `EXPECTED_API_NAMES` and the plugin descriptor API/dataset names.

- [ ] **Step 5: Implement the Servlet-only production Bean graph**

Create `ApplicationConfiguration` with this exact Bean structure:

```java
package com.akkc.tensor.config;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.GenericQueryRepository;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.observability.TensorMetrics;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class ApplicationConfiguration {
    @Bean
    public PluginRegistry pluginRegistry(List<DataSourcePlugin> plugins) {
        return new PluginRegistry(plugins);
    }

    @Bean("tensorDatasetAdapters")
    public List<DatasetAdapter> tensorDatasetAdapters(
            @Qualifier("tushareDatasetDefinitions")
                    List<DatasetDefinition> tushareDefinitions,
            ObjectProvider<DatasetAdapter> extensions) {
        ValueConverter converter = new ValueConverter();
        FingerprintKeyCodec keyCodec = new FingerprintKeyCodec();
        List<DatasetAdapter> adapters = new ArrayList<>();
        tushareDefinitions.forEach(definition -> adapters.add(
                new GenericDatasetAdapter(definition, converter, keyCodec)));
        extensions.orderedStream().forEach(adapters::add);
        return List.copyOf(adapters);
    }

    @Bean
    @DependsOnDatabaseInitialization
    public DatasetCatalog datasetCatalog(
            @Qualifier("tensorDatasetAdapters") List<DatasetAdapter> adapters,
            DataSource dataSource) {
        return new DatasetStartupValidator(
                adapters.stream().map(DatasetAdapter::definition).toList(),
                new SchemaInspector(dataSource)).validate();
    }

    @Bean
    public AdapterRegistry adapterRegistry(
            @Qualifier("tensorDatasetAdapters") List<DatasetAdapter> adapters,
            DatasetCatalog catalog) {
        return new AdapterRegistry(adapters.stream()
                .filter(adapter -> catalog.find(adapter.datasetKey()).isPresent())
                .toList());
    }

    @Bean
    public ParameterValidator parameterValidator() {
        return new ParameterValidator();
    }

    @Bean
    public DatasetLockManager datasetLockManager() {
        return new DatasetLockManager();
    }

    @Bean
    public ExistingKeyRepository existingKeyRepository(JdbcTemplate jdbcTemplate) {
        return new ExistingKeyRepository(jdbcTemplate);
    }

    @Bean
    public GenericUpsertRepository genericUpsertRepository(JdbcTemplate jdbcTemplate) {
        return new GenericUpsertRepository(jdbcTemplate);
    }

    @Bean
    public PersistenceService persistenceService(
            DatasetCatalog catalog,
            DatasetLockManager lockManager,
            ExistingKeyRepository existingKeys,
            GenericUpsertRepository upserts,
            PlatformTransactionManager transactions) {
        return new PersistenceService(
                catalog, lockManager, existingKeys, upserts, transactions);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DownloadService downloadService(
            PluginRegistry plugins,
            AdapterRegistry adapters,
            ParameterValidator validator,
            PersistenceService persistence,
            Clock clock) {
        return new DownloadService(
                plugins, adapters, validator, persistence, clock);
    }

    @Bean
    public GenericQueryRepository genericQueryRepository(JdbcTemplate jdbcTemplate) {
        return new GenericQueryRepository(jdbcTemplate);
    }

    @Bean
    public DatasetQueryService datasetQueryService(
            DatasetCatalog catalog, GenericQueryRepository repository) {
        return new DatasetQueryService(catalog, repository);
    }

    @Bean
    public TensorMetrics tensorMetrics(
            MeterRegistry meterRegistry, PluginRegistry plugins) {
        return new TensorMetrics(meterRegistry, plugins);
    }

    @Bean
    public OperationLogger operationLogger(
            PluginRegistry plugins, TensorMetrics metrics) {
        return new OperationLogger(plugins, metrics);
    }
}
```

Do not annotate Core classes or create a second `JdbcTemplate`, transaction manager, schema inspector Bean, or plugin-specific service.

- [ ] **Step 6: Implement the exact bounded metric surface**

Create `TensorMetrics` as a final class. Its complete public API is the constructor, nested `Outcome`, `supports`, `recordDownload`, and `recordQuery`:

```java
package com.akkc.tensor.observability;

import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TensorMetrics {
    private static final String DOWNLOAD_TOTAL = "tensor_download_total";
    private static final String DOWNLOAD_DURATION =
            "tensor_download_duration_seconds";
    private static final String DOWNLOAD_ROWS = "tensor_download_rows_total";
    private static final String QUERY_TOTAL = "tensor_query_total";
    private static final String QUERY_DURATION =
            "tensor_query_duration_seconds";

    private final MeterRegistry registry;
    private final Set<DatasetKey> supported;

    public TensorMetrics(MeterRegistry registry, PluginRegistry plugins) {
        this.registry = Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(plugins, "plugins");
        supported = plugins.descriptors().stream()
                .flatMap(descriptor -> descriptor.datasets().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean supports(DatasetKey key) {
        return supported.contains(Objects.requireNonNull(key, "key"));
    }

    public void recordDownload(
            DatasetKey key,
            Outcome outcome,
            Duration duration,
            long sourceRows,
            long insertedRows,
            long updatedRows) {
        requireDuration(duration);
        requireRows(sourceRows, insertedRows, updatedRows);
        if (!supports(key)) {
            return;
        }
        counter(DOWNLOAD_TOTAL, key, "outcome", outcome.value()).increment();
        timer(DOWNLOAD_DURATION, key, outcome).record(duration);
        if (outcome != Outcome.FAILURE) {
            counter(DOWNLOAD_ROWS, key, "kind", "source").increment(sourceRows);
            counter(DOWNLOAD_ROWS, key, "kind", "inserted").increment(insertedRows);
            counter(DOWNLOAD_ROWS, key, "kind", "updated").increment(updatedRows);
        }
    }

    public void recordQuery(
            DatasetKey key, Outcome outcome, Duration duration) {
        requireDuration(duration);
        if (outcome == Outcome.EMPTY) {
            throw new IllegalArgumentException("Query outcome must not be empty");
        }
        if (!supports(key)) {
            return;
        }
        counter(QUERY_TOTAL, key, "outcome", outcome.value()).increment();
        timer(QUERY_DURATION, key, outcome).record(duration);
    }

    private Counter counter(
            String name, DatasetKey key, String extraName, String extraValue) {
        return Counter.builder(name)
                .tags("plugin", key.pluginId().value(),
                        "api", key.apiName().value(), extraName, extraValue)
                .register(registry);
    }

    private Timer timer(String name, DatasetKey key, Outcome outcome) {
        return Timer.builder(name)
                .tags("plugin", key.pluginId().value(),
                        "api", key.apiName().value(),
                        "outcome", outcome.value())
                .register(registry);
    }

    private static void requireDuration(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    private static void requireRows(long source, long inserted, long updated) {
        if (source < 0 || inserted < 0 || updated < 0) {
            throw new IllegalArgumentException("row counts must not be negative");
        }
    }

    public enum Outcome {
        SUCCESS("success"),
        EMPTY("empty"),
        FAILURE("failure");

        private final String value;

        Outcome(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
```

Do not use `Tags.of(Map)`, common tags, a public string-label method, percentiles, request IDs, error codes, or exception values as tags.

- [ ] **Step 7: Implement safe one-event operation wrapping**

Create `OperationLogger` with the approved constructor/method signatures. Use one private `Failure` record, one `Pattern.compile("token|authorization|cookie|password|credential", CASE_INSENSITIVE)`, and these exact wrappers:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogger.class);
private static final Pattern SENSITIVE_NAME = Pattern.compile(
        "token|authorization|cookie|password|credential",
        Pattern.CASE_INSENSITIVE);

private final Map<DatasetKey, List<String>> parameterNames;
private final TensorMetrics metrics;

public OperationLogger(PluginRegistry plugins, TensorMetrics metrics) {
    Objects.requireNonNull(plugins, "plugins");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    LinkedHashMap<DatasetKey, List<String>> names = new LinkedHashMap<>();
    plugins.descriptors().forEach(descriptor -> descriptor.apis().forEach(api -> {
        DatasetKey key = DatasetKey.of(descriptor.pluginId(), api.apiName());
        if (descriptor.datasets().contains(key)) {
            names.putIfAbsent(key, api.parameters().stream()
                    .map(ParameterDescriptor::name)
                    .toList());
        }
    }));
    parameterNames = Collections.unmodifiableMap(names);
}
```

```java
public DownloadResponse download(
        DatasetKey key,
        Map<String, Object> parameters,
        Supplier<DownloadResponse> operation) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(parameters, "parameters");
    Objects.requireNonNull(operation, "operation");
    if (!metrics.supports(key)) {
        return operation.get();
    }
    String requestId = requestId();
    List<String> summary = parameterNames.getOrDefault(key, List.of()).stream()
            .filter(parameters::containsKey)
            .filter(name -> !SENSITIVE_NAME.matcher(name).find())
            .toList();
    long started = System.nanoTime();
    try {
        DownloadResponse response = Objects.requireNonNull(
                operation.get(), "download response");
        Duration duration = elapsed(started);
        TensorMetrics.Outcome outcome = response.outcome() == DownloadOutcome.EMPTY
                ? TensorMetrics.Outcome.EMPTY
                : TensorMetrics.Outcome.SUCCESS;
        recordDownloadMetrics(key, outcome, duration,
                response.sourceRowCount(), response.insertedRows(), response.updatedRows());
        LOGGER.info(
                "tensor.operation.completed requestId={} operation=download pluginId={} apiName={} paramSummary={} sourceRowCount={} insertedRows={} updatedRows={} durationMs={} outcome={} failureStage=none errorCode=none",
                requestId, key.pluginId().value(), key.apiName().value(), summary,
                response.sourceRowCount(), response.insertedRows(), response.updatedRows(),
                duration.toMillis(), outcome.value());
        return response;
    } catch (RuntimeException failure) {
        Duration duration = elapsed(started);
        Failure classified = downloadFailure(failure);
        recordDownloadMetrics(key, TensorMetrics.Outcome.FAILURE, duration, 0, 0, 0);
        LOGGER.info(
                "tensor.operation.completed requestId={} operation=download pluginId={} apiName={} paramSummary={} sourceRowCount=unavailable insertedRows=unavailable updatedRows=unavailable durationMs={} outcome=failure failureStage={} errorCode={}",
                requestId, key.pluginId().value(), key.apiName().value(), summary,
                duration.toMillis(), classified.stage(), classified.code());
        throw failure;
    }
}
```

The query wrapper follows the same identity-preserving pattern:

```java
public PageResponse query(
        DatasetKey key,
        List<String> filterNames,
        int requestedPage,
        int requestedPageSize,
        Supplier<PageResponse> operation) {
    Objects.requireNonNull(key, "key");
    filterNames = List.copyOf(Objects.requireNonNull(filterNames, "filterNames"));
    Objects.requireNonNull(operation, "operation");
    if (!metrics.supports(key)) {
        return operation.get();
    }
    String requestId = requestId();
    long started = System.nanoTime();
    try {
        PageResponse response = Objects.requireNonNull(
                operation.get(), "query response");
        Duration duration = elapsed(started);
        recordQueryMetrics(key, TensorMetrics.Outcome.SUCCESS, duration);
        LOGGER.info(
                "tensor.operation.completed requestId={} operation=query pluginId={} apiName={} filterNames={} page={} pageSize={} resultCount={} totalElements={} durationMs={} outcome=success failureStage=none errorCode=none",
                requestId, key.pluginId().value(), key.apiName().value(), filterNames,
                response.page(), response.pageSize(), response.items().size(),
                response.totalElements(), duration.toMillis());
        return response;
    } catch (RuntimeException failure) {
        Duration duration = elapsed(started);
        Failure classified = domainFailure(failure, ErrorCode.QUERY_FAILED, "query");
        recordQueryMetrics(key, TensorMetrics.Outcome.FAILURE, duration);
        LOGGER.info(
                "tensor.operation.completed requestId={} operation=query pluginId={} apiName={} filterNames={} page={} pageSize={} resultCount=unavailable totalElements=unavailable durationMs={} outcome=failure failureStage={} errorCode={}",
                requestId, key.pluginId().value(), key.apiName().value(), filterNames,
                requestedPage, requestedPageSize, duration.toMillis(),
                classified.stage(), classified.code());
        throw failure;
    }
}
```

Populate the constructor's immutable parameter-name map from `PluginRegistry.descriptors()`: for every descriptor API whose `DatasetKey` is present in `descriptor.datasets()`, retain the API parameter names in declared order with `putIfAbsent`. Implement helpers exactly by these rules:

```java
private static Failure downloadFailure(RuntimeException failure) {
    if (failure instanceof DataAccessException
            || failure instanceof TransactionException) {
        return new Failure(ErrorCode.PERSISTENCE_FAILED, "persistence");
    }
    return domainFailure(failure, ErrorCode.INTERNAL_ERROR, "internal");
}

private static Failure domainFailure(
        RuntimeException failure, ErrorCode fallback, String fallbackStage) {
    if (!(failure instanceof TensorException tensor)) {
        return new Failure(fallback, fallbackStage);
    }
    ErrorCode code = tensor.code();
    return new Failure(code, switch (code) {
        case PARAM_REQUIRED, PARAM_INVALID -> "parameter";
        case PLUGIN_DISABLED, DATASET_MISCONFIGURED -> "registration";
        case SOURCE_AUTH_FAILED, SOURCE_PERMISSION_DENIED, SOURCE_RATE_LIMITED,
                SOURCE_UNAVAILABLE, SOURCE_NETWORK_ERROR, SOURCE_TIMEOUT,
                SOURCE_PAYLOAD_INVALID -> "source";
        case ADAPTER_FIELD_MISSING, ADAPTER_TYPE_INVALID -> "adapter";
        case PERSISTENCE_FAILED -> "persistence";
        case QUERY_FAILED -> "query";
        case INTERNAL_ERROR -> "internal";
    });
}

private static Duration elapsed(long started) {
    return Duration.ofNanos(Math.max(0L, System.nanoTime() - started));
}

private static String requestId() {
    String value = MDC.get(RequestIdFilter.MDC_KEY);
    if (value == null || value.isBlank()) {
        throw new IllegalStateException("Request ID is unavailable");
    }
    return value;
}
```

`recordDownloadMetrics` and `recordQueryMetrics` call `TensorMetrics` inside `try/catch (RuntimeException)` so a registry failure cannot replace a business result/exception. On such failure, write only `tensor.observation.failed operation=download|query` at WARN with no Throwable argument or original message. Keep the normal completion event even when metric recording fails.

```java
private void recordDownloadMetrics(
        DatasetKey key,
        TensorMetrics.Outcome outcome,
        Duration duration,
        long sourceRows,
        long insertedRows,
        long updatedRows) {
    try {
        metrics.recordDownload(
                key, outcome, duration, sourceRows, insertedRows, updatedRows);
    } catch (RuntimeException ignored) {
        LOGGER.warn("tensor.observation.failed operation=download");
    }
}

private void recordQueryMetrics(
        DatasetKey key,
        TensorMetrics.Outcome outcome,
        Duration duration) {
    try {
        metrics.recordQuery(key, outcome, duration);
    } catch (RuntimeException ignored) {
        LOGGER.warn("tensor.observation.failed operation=query");
    }
}

private record Failure(ErrorCode code, String stage) {
}
```

- [ ] **Step 8: Wire the two Controllers without changing their HTTP behavior**

Add one final `OperationLogger` field and constructor parameter to each Controller. In `DownloadController.download`, preserve the null/MDC checks, then replace the direct service call with:

```java
DatasetKey key = DatasetKey.of(
        PluginId.of(request.pluginId()), ApiName.of(request.apiName()));
RequestId requestId = new RequestId(UUID.fromString(value));
return operationLogger.download(key, request.params(), () -> DownloadResponse.from(
        downloadService.execute(
                key.pluginId(), key.apiName(), request.params(), requestId)));
```

In `DatasetController.listDatasetRecords`, after catalog/filter/criteria/MDC validation, construct filter names without values:

```java
List<String> filterNames = new ArrayList<>();
if (tsCode != null) {
    filterNames.add("ts_code");
}
if (tradeDateFrom != null || tradeDateTo != null) {
    filterNames.add("trade_date");
}
if (annDateFrom != null || annDateTo != null) {
    filterNames.add("ann_date");
}
return operationLogger.query(key, filterNames, page, pageSize, () -> {
    try {
        return PageResponse.from(
                requestId, key, datasetQueryService.query(key, criteria));
    } catch (IllegalArgumentException exception) {
        throw new DatasetQueryAccessException();
    }
});
```

Update the two standalone IT builders only. Each test class gets this helper and passes it to the new constructor:

```java
private static OperationLogger operationLogger() {
    PluginRegistry plugins = new PluginRegistry(List.of());
    return new OperationLogger(
            plugins, new TensorMetrics(new SimpleMeterRegistry(), plugins));
}
```

The empty whitelist deliberately executes existing suppliers without adding events/meters. Do not weaken any existing assertion, add overloaded production constructors, or expose a no-op logger API.

- [ ] **Step 9: Implement the ordered security and cache filter**

Create `WebSecurityHeadersConfiguration` with no Spring Security dependency:

```java
package com.akkc.tensor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class WebSecurityHeadersConfiguration {
    private static final String CSP =
            "default-src 'self'; base-uri 'none'; object-src 'none'; "
                    + "frame-ancestors 'none'; form-action 'self'; "
                    + "script-src 'self'; style-src 'self'; "
                    + "img-src 'self' data:; font-src 'self'; connect-src 'self'";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> securityHeadersFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                response.setHeader("Content-Security-Policy", CSP);
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("Referrer-Policy", "no-referrer");
                response.setHeader(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()");
                response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
                response.setHeader("Cache-Control", cacheControl(path(request)));
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setName("tensorSecurityHeadersFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context.isEmpty() ? uri : uri.substring(context.length());
    }

    private static String cacheControl(String path) {
        if (path.startsWith("/assets/")) {
            return "public, max-age=31536000, immutable";
        }
        if ("/".equals(path)
                || "/index.html".equals(path)
                || path.startsWith("/api/")
                || "/actuator".equals(path)
                || path.startsWith("/actuator/")) {
            return "no-store";
        }
        return "no-cache";
    }
}
```

Ordering is expressed by the frozen `Ordered.HIGHEST_PRECEDENCE + 1` value. Do not add HSTS, CORS, authentication, resource handlers, or SPA forwarding.

- [ ] **Step 10: Finish the 18 ordinary tests and obtain focused GREEN plus affected regression**

Replace the temporary missing-type compilation state with the complete assertions from Step 2. Keep `ObservabilityTest` at exactly 18 invocations and run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=ObservabilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 18/18, zero failures/errors/skips. Then run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare,tensor-app -am \
  -Dtest=TushareProPluginTest,DownloadControllerIT,DatasetControllerIT,GlobalExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: Tushare 8/8, app 43/43, total 51/51; no changed HTTP response, transaction, query, request-ID, or safe-error behavior. Save complete outputs to `/private/tmp/m09-t06-focused.log` and `/private/tmp/m09-t06-regression.log`, outside Git.

- [ ] **Step 11: Complete and run the real production Servlet context test**

Implement `ProductionApplicationContextIT.start` with these exact property keys; use the Testcontainer values and never print them:

```java
private static ConfigurableApplicationContext start(
        MySQLContainer<?> mysql, String token) {
    return new SpringApplicationBuilder(TensorApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties(
                    "server.port=0",
                    "spring.profiles.active=production",
                    "spring.datasource.hikari.connection-timeout=250",
                    "TENSOR_DB_URL=" + mysql.getJdbcUrl(),
                    "TENSOR_DB_USERNAME=" + mysql.getUsername(),
                    "TENSOR_DB_PASSWORD=" + SECRET,
                    "TENSOR_TUSHARE_TOKEN=" + token)
            .run();
}
```

For each context, obtain the port from `WebServerApplicationContext`, call HTTP with the existing `RestClient`, and inspect status/body through `exchange` so 404/503 responses are assertions rather than thrown client exceptions. Assert unique Beans for `PluginRegistry`, `DatasetCatalog`, `AdapterRegistry`, `DownloadService`, `DatasetQueryService`, `TensorMetrics`, `OperationLogger`, all three Controllers, `GlobalExceptionHandler`, and the named security registration. Obtain the named Tushare definition list, assert 49, and assert every definition key is present in both catalog and adapter registry.

The first context uses empty token and must return:

```text
GET /actuator/health -> 200, status UP, components.db.status UP
GET /api/v1/data-sources -> 200, Tushare credentialConfigured=false and downloadAvailable=false
GET /actuator -> 404
GET /actuator/env -> 404
GET /actuator/configprops -> 404
GET /actuator/metrics -> 404
```

All six responses have request/security headers and contain neither `SECRET`, the JDBC URL, nor username. Close that context. Attach a root Logback `ListAppender`, start the second context with `SECRET` as Token, repeat the response scans, and assert captured formatted/Throwable text does not contain the shared Token/password sentinel. Stop the MySQL container, call `/actuator/health` once with the fixed 250 ms pool timeout, and assert HTTP 503 with root status DOWN. Detach/stop the appender and close context in `finally`.

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=ProductionApplicationContextIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=FlywaySchemaContractIT,ProductionApplicationContextIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 1/1, then 53/53; zero failures/errors/skips on fixed MySQL 8.4.6; no Tushare network call. Save logs under `/private/tmp`, never the repository.

- [ ] **Step 12: Prove the five required mutation guards**

Apply exactly one temporary change at a time, run the focused ordinary test, record the failed invocation count, then restore only that temporary change with `apply_patch`:

1. Remove the `metrics.supports(key)` guard in both wrappers. Expected: `skipsUnknownKeysWithoutSkippingTheOperation` fails because an event or meter appears.
2. Add `parameters.values()` or `failure.getMessage()` to the completion log. Expected: `removesSecretsValuesAndThrowableTextFromCompletionEvents` fails.
3. Change `tensor_download_rows_total` or one `kind` value. Expected: download-success or frozen-meter-schema tests fail.
4. Replace `throw failure` with `throw new IllegalStateException()`. Expected: the six download failure invocations and query-failure identity assertion fail.
5. Change YAML exposure to `[health, metrics]` or health details to `always`. Expected: YAML defaults and production endpoint/security assertions fail.

After every restoration, run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=ObservabilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 18/18. The final working diff must contain no mutation residue.

- [ ] **Step 13: Run full regression and all static/security/range gates**

Run in the self-attach-capable environment:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

Expected twice: plugin-api 79, core 75, Tushare 93, fixture 12, app 79, total 338/338; zero failures/errors/skips; six Enforcer executions, app ArchUnit, and forbidden-Git gates pass.

Run the design's authorized-symbol, secret, configuration, JAR, protected-path, format, and scope commands. Additionally inspect the staged paths:

```bash
git status --short --untracked-files=all -- data-plane
git diff --check
git diff --name-only
```

Expected: exactly the 13 Files entries; no POM/Core/plugin-api/fixture/migration/DTO/handler/filter change; no `target/` or log; all seven new files are tracked before commit. Any broad secret scan must search output files as well as source and must not print actual configured secrets.

- [ ] **Step 14: Request one focused review and apply at most one consolidated fix wave**

Use `superpowers:requesting-code-review` once with `docs/task-designs/M09-T06-design.md`, this plan, the 13-file diff, RED/GREEN/mutation logs, and verification totals. Require findings classified as Critical, Important, or Minor, with exact file/line evidence and a final `Ready to merge: Yes|No`.

If findings exist, verify them against the approved contract, apply one consolidated minimal fix wave only inside the 13-file scope, then rerun the directly affected focused tests, production context 1/1 when runtime wiring changed, and default reactor verify 338/338. Do not apply speculative refactors or broaden dependencies.

- [ ] **Step 15: Commit the exact implementation**

Stage only:

```bash
git add \
  data-plane/tensor-app/src/main/resources/application.yml \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/TensorMetrics.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/OperationLogger.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ObservabilityTest.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ProductionApplicationContextIT.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java
git diff --cached --check
git diff --cached --name-status
git commit -m "feat(app): add safe configuration and observability"
```

Expected: one implementation commit with exactly 13 paths, seven added and six modified. Documentation, task state, generated files, and temporary evidence are excluded.

- [ ] **Step 16: Verify the committed state, clean generated output, and collect final evidence**

Run:

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-status HEAD
git diff --check HEAD^ HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
```

Expected: fixed message and 13 paths; clean succeeds; worktree empty; no `target/` remains. Then freshly rerun the explicit production context 1/1 and default reactor verify 338/338 from the committed source. Record exact module counts, mutation failures, endpoint statuses, secret-scan results, and review conclusion for the task-board completion evidence. Do not mark M09-T06 complete if any required result is missing.
