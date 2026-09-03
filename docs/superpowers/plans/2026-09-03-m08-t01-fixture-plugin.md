# M08-T01 Fixture Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the acceptance-only `fixture/fixture_daily` plugin, its exact metadata contract, and a real `GenericDatasetAdapter` Bean without implementing M08-T02 scenarios.

**Architecture:** `FixtureConfiguration` owns one immutable programmatic `DatasetDefinition` and registers `FixturePlugin` plus `GenericDatasetAdapter` only when profile `acceptance` and property `tensor.plugins.fixture.enabled=true` are both active. The fixture module gains the approved core/Spring dependency edge; ArchUnit permits only `fixture -> core`, while the YAML remains a tested contract resource and the plugin safely rejects downloads until M08-T02 injects the scenario factory.

**Tech Stack:** Java 21, Maven reactor, Spring Framework/Boot conditional configuration, JUnit 5, AssertJ, ArchUnit

**Spec:** `docs/task-designs/M08-T01-design.md`

## Global Constraints

- Work directly on `main`; do not create a worktree.
- Use only the six implementation files listed below and add all four new files to Git.
- Add only `tensor-core` and `spring-boot-autoconfigure` as new fixture compile dependencies, with no explicit versions.
- Keep fixture independent of Tushare and app; keep core and Tushare dependency rules unchanged.
- Register fixture Beans only for active profile `acceptance` plus exact property value `true`.
- Build the single definition in Java and test exact parity with `datasets/fixture/fixture_daily.yaml`; do not add a YAML loader.
- Reuse `GenericDatasetAdapter`, `ValueConverter`, and `FingerprintKeyCodec`; do not create fixture-specific adaptation logic.
- Until M08-T02, report unavailable readiness and throw `SOURCE_UNAVAILABLE` with `Fixture scenarios are not configured` for the known API.
- Do not add M08-T02 scenarios, M08-T03 integration wiring, database access, network access, production configuration, credentials, retries, or logs.
- Keep the implementation commit message exactly `feat(fixture): add acceptance data-source plugin`.

---

### Task 1: Deliver the Conditional Fixture Plugin Contract

**Files:**
- Modify: `data-plane/tensor-plugin-fixture/pom.xml`
- Modify: `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java`
- Create: `data-plane/tensor-plugin-fixture/src/main/resources/datasets/fixture/fixture_daily.yaml`
- Test: `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java`
- Create: `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`
- Create: `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`

**Interfaces:**
- Consumes: `DataSourcePlugin.descriptor()`, `readiness()`, `download(ApiName, Map<String,Object>)`; `DatasetAdapter`; `SourceException(ErrorCode,String)`; `GenericDatasetAdapter(DatasetDefinition,ValueConverter,FingerprintKeyCodec)`.
- Produces: public final `FixturePlugin(DatasetDefinition)` implementing the exact `DataSourcePlugin` surface; public final conditional `FixtureConfiguration`; Spring Beans named `fixturePlugin` and `fixtureDatasetAdapter`; classpath resource `datasets/fixture/fixture_daily.yaml`.

- [ ] **Step 1: Reconfirm the clean baseline**

Run:

```bash
git status --short
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

Expected: status has no output. Maven exits 0 with plugin-api 79, core 75, Tushare 93, fixture 0, app 13, total 260/260, no failures/errors/skips, and all six Enforcer executions plus `ModuleDependencyTest` passing. If only Mockito/Byte Buddy attach fails in a restricted JVM, rerun unchanged where attach is allowed; do not edit or skip tests.

- [ ] **Step 2: Add only the approved dependencies and architecture edge**

In `data-plane/tensor-plugin-fixture/pom.xml`, keep the existing plugin-api dependency first and insert exactly:

```xml
        <dependency>
            <groupId>com.akkc.tensor</groupId>
            <artifactId>tensor-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
```

In `ModuleDependencyTest.enforces_module_dependency_direction`, replace only the fixture rule with:

```java
        ArchRule fixture = noClasses().that().resideInAPackage("..plugin.fixture..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.akkc.tensor.plugin.tushare..", "com.akkc.tensor.app..");
```

Do not alter the plugin-api, core, or Tushare rules.

- [ ] **Step 3: Create the exact YAML contract**

Create `data-plane/tensor-plugin-fixture/src/main/resources/datasets/fixture/fixture_daily.yaml` with exactly:

```yaml
pluginId: fixture
apiName: fixture_daily
tableName: fixture__fixture_daily
category: 验收
displayName: Fixture 日线
queryMode: trade_date
parameters:
  - name: scenario
    label: 场景
    description: 确定性验收场景
    type: ENUM
    required: true
    defaultValue: SUCCESS
    allowedValues: [SUCCESS, EMPTY, SOURCE_FAILURE, TYPE_FAILURE, PERSISTENCE_FAILURE]
columns:
  - { name: ts_code, label: ts_code, logicalType: STRING, nullable: false, displayOrder: 0, length: 64 }
  - { name: trade_date, label: trade_date, logicalType: DATE, nullable: false, displayOrder: 1 }
  - { name: amount, label: amount, logicalType: DECIMAL, nullable: false, displayOrder: 2, precision: 38, scale: 18 }
  - { name: note, label: note, logicalType: STRING, nullable: true, displayOrder: 3, length: 255 }
businessKey: { mode: COMPOSITE, fields: [ts_code, trade_date] }
filters: [ts_code]
fixedColumn: ts_code
```

Keep the final newline. Do not copy `contracts/dataset-definition.schema.json` into this module because that schema currently requires a Tushare table name.

- [ ] **Step 4: Write all six failing tests before production classes**

Create `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java` with this complete test contract:

```java
package com.akkc.tensor.plugin.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class FixturePluginTest {
    private static final PluginId PLUGIN_ID = PluginId.of("fixture");
    private static final ApiName API_NAME = ApiName.of("fixture_daily");
    private static final String UNAVAILABLE = "Fixture scenarios are not configured";
    private static final String YAML = """
            pluginId: fixture
            apiName: fixture_daily
            tableName: fixture__fixture_daily
            category: 验收
            displayName: Fixture 日线
            queryMode: trade_date
            parameters:
              - name: scenario
                label: 场景
                description: 确定性验收场景
                type: ENUM
                required: true
                defaultValue: SUCCESS
                allowedValues: [SUCCESS, EMPTY, SOURCE_FAILURE, TYPE_FAILURE, PERSISTENCE_FAILURE]
            columns:
              - { name: ts_code, label: ts_code, logicalType: STRING, nullable: false, displayOrder: 0, length: 64 }
              - { name: trade_date, label: trade_date, logicalType: DATE, nullable: false, displayOrder: 1 }
              - { name: amount, label: amount, logicalType: DECIMAL, nullable: false, displayOrder: 2, precision: 38, scale: 18 }
              - { name: note, label: note, logicalType: STRING, nullable: true, displayOrder: 3, length: 255 }
            businessKey: { mode: COMPOSITE, fields: [ts_code, trade_date] }
            filters: [ts_code]
            fixedColumn: ts_code
            """;

    @Test
    void exposesExactPublicSurfaceAndConstructorBoundaries() {
        assertThat(FixturePlugin.class.getModifiers()).satisfies(modifiers ->
                assertThat(java.lang.reflect.Modifier.isFinal(modifiers)).isTrue());
        assertThat(FixtureConfiguration.class.getModifiers()).satisfies(modifiers ->
                assertThat(java.lang.reflect.Modifier.isFinal(modifiers)).isTrue());
        assertThat(FixturePlugin.class.getConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).containsExactly(DatasetDefinition.class));
        assertThat(FixturePlugin.class.getInterfaces()).containsExactly(DataSourcePlugin.class);
        assertThat(FixturePlugin.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("descriptor", "readiness", "download");

        assertThatNullPointerException().isThrownBy(() -> new FixturePlugin(null));
        DatasetDefinition valid = expectedDefinition();
        DatasetKey wrongKey = DatasetKey.of(PLUGIN_ID, ApiName.of("fixture_other"));
        DatasetDefinition wrong = new DatasetDefinition(
                wrongKey, valid.displayName(), valid.category(), valid.queryMode(), valid.parameters(),
                TableName.from(wrongKey), valid.columns(), valid.businessKey(), valid.filters(),
                valid.fixedColumn(), valid.batchSize());
        assertThatThrownBy(() -> new FixturePlugin(wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("definition must be fixture_daily");
    }

    @Test
    void exposesExactDescriptorAndInterimReadiness() {
        FixturePlugin plugin = new FixturePlugin(expectedDefinition());
        var descriptor = plugin.descriptor();

        assertThat(descriptor.pluginId()).isEqualTo(PLUGIN_ID);
        assertThat(descriptor.displayName()).isEqualTo("Fixture");
        assertThat(descriptor.description()).isEqualTo("Fixture 验收数据源");
        assertThat(plugin.readiness()).isSameAs(plugin.readiness())
                .isEqualTo(new PluginReadiness(true, true, false, UNAVAILABLE));
        assertThat(descriptor.enabled()).isTrue();
        assertThat(descriptor.credentialConfigured()).isTrue();
        assertThat(descriptor.downloadAvailable()).isFalse();
        assertThat(descriptor.unavailableReason()).isEqualTo(UNAVAILABLE);
        assertThat(descriptor.datasets()).containsExactly(expectedDefinition().datasetKey());
        assertThat(descriptor.apis()).singleElement().satisfies(api -> {
            assertThat(api.apiName()).isEqualTo(API_NAME);
            assertThat(api.displayName()).isEqualTo("Fixture 日线");
            assertThat(api.category()).isEqualTo("验收");
            assertThat(api.queryMode()).isEqualTo(QueryMode.trade_date);
            assertThat(api.parameters()).containsExactly(expectedParameter());
        });
    }

    @Test
    void matchesExactJavaAndYamlMetadata() throws IOException {
        try (AnnotationConfigApplicationContext context = context("acceptance", "true")) {
            assertThat(context.getBean(DatasetAdapter.class).definition()).isEqualTo(expectedDefinition());
        }
        try (var input = FixturePluginTest.class.getClassLoader()
                .getResourceAsStream("datasets/fixture/fixture_daily.yaml")) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(YAML);
        }
    }

    @Test
    void registersPluginAndGenericAdapterOnlyWhenBothConditionsMatch() {
        try (AnnotationConfigApplicationContext context = context("acceptance", "true")) {
            assertThat(context.getBeansOfType(FixturePlugin.class)).hasSize(1);
            assertThat(context.getBeansOfType(DatasetAdapter.class)).hasSize(1);
            DatasetAdapter adapter = context.getBean(DatasetAdapter.class);
            assertThat(adapter).isExactlyInstanceOf(GenericDatasetAdapter.class);
            assertThat(adapter.definition()).isEqualTo(expectedDefinition());

            DownloadEnvelope envelope = new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"),
                    List.of("ts_code", "trade_date", "amount", "note"), 1,
                    List.of(Arrays.<Object>asList("000001.SZ", "20260807", "11.23", null)),
                    DownloadStatus.SUCCESS, null);
            var batch = adapter.adapt(envelope, Instant.parse("2026-08-07T00:00:00Z"));
            assertThat(batch.rows()).singleElement().satisfies(row -> {
                assertThat(row.get("ts_code")).isEqualTo("000001.SZ");
                assertThat(row.get("trade_date")).isEqualTo(LocalDate.of(2026, 8, 7));
                assertThat(row.get("amount")).isEqualTo(new BigDecimal("11.230000000000000000"));
                assertThat(row).containsEntry("note", null);
            });
            assertThat(batch.businessKeyDefinition())
                    .isEqualTo(new BusinessKeyDefinition(
                            BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")));
        }
    }

    @Test
    void staysAbsentOutsideAcceptanceEvenWhenEnabled() {
        try (AnnotationConfigApplicationContext context = context(null, "true")) {
            assertNoFixtureBeans(context);
        }
        try (AnnotationConfigApplicationContext context = context("production", "true")) {
            assertNoFixtureBeans(context);
        }
    }

    @Test
    void staysAbsentWhenDisabledAndRejectsInterimDownloadsSafely() {
        try (AnnotationConfigApplicationContext context = context("acceptance", null)) {
            assertNoFixtureBeans(context);
        }
        try (AnnotationConfigApplicationContext context = context("acceptance", "false")) {
            assertNoFixtureBeans(context);
        }

        FixturePlugin plugin = new FixturePlugin(expectedDefinition());
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of("scenario", "SUCCESS")))
                .isInstanceOfSatisfying(SourceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo(UNAVAILABLE);
                    assertThat(exception.retryable()).isTrue();
                });
        assertThatThrownBy(() -> plugin.download(ApiName.of("fixture_other"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown Fixture API");
        assertThatNullPointerException().isThrownBy(() -> plugin.download(null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> plugin.download(API_NAME, null));
    }

    private static ParameterDescriptor expectedParameter() {
        return new ParameterDescriptor(
                "scenario", "场景", "确定性验收场景", ParameterType.ENUM, true, "SUCCESS",
                List.of("SUCCESS", "EMPTY", "SOURCE_FAILURE", "TYPE_FAILURE", "PERSISTENCE_FAILURE"),
                null, null);
    }

    private static DatasetDefinition expectedDefinition() {
        DatasetKey key = DatasetKey.of(PLUGIN_ID, API_NAME);
        return new DatasetDefinition(
                key,
                "Fixture 日线",
                "验收",
                QueryMode.trade_date,
                List.of(expectedParameter()),
                TableName.from(key),
                List.of(
                        column("ts_code", LogicalType.STRING, false, 0, 64, null, null),
                        column("trade_date", LogicalType.DATE, false, 1, null, null, null),
                        column("amount", LogicalType.DECIMAL, false, 2, null, 38, 18),
                        column("note", LogicalType.STRING, true, 3, 255, null, null)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(new FilterDefinition("ts_code")),
                "ts_code");
    }

    private static ColumnDefinition column(
            String name, LogicalType type, boolean nullable, int order,
            Integer length, Integer precision, Integer scale) {
        return new ColumnDefinition(
                name, name, type, nullable, order, length, precision, scale, List.of(), false);
    }

    private static AnnotationConfigApplicationContext context(String profile, String enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (profile != null) {
            context.getEnvironment().setActiveProfiles(profile);
        }
        if (enabled != null) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "fixture-test", Map.of("tensor.plugins.fixture.enabled", enabled)));
        }
        context.register(FixtureConfiguration.class);
        context.refresh();
        return context;
    }

    private static void assertNoFixtureBeans(AnnotationConfigApplicationContext context) {
        assertThat(context.getBeansOfType(FixturePlugin.class)).isEmpty();
        assertThat(context.getBeansOfType(DatasetAdapter.class)).isEmpty();
    }
}
```

Do not create either production class yet.

- [ ] **Step 5: Run the focused command and verify a genuine RED**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am \
  -Dtest=FixturePluginTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: nonzero exit in fixture `testCompile`, with missing symbols `FixturePlugin` and `FixtureConfiguration`. The test source, POM resolution, YAML path, plugin-api, and core must not fail first.

- [ ] **Step 6: Implement the minimal plugin**

Create `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`:

```java
package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FixturePlugin implements DataSourcePlugin {
    private static final DatasetKey DATASET_KEY =
            DatasetKey.of(PluginId.of("fixture"), ApiName.of("fixture_daily"));
    private static final String UNAVAILABLE = "Fixture scenarios are not configured";

    private final PluginReadiness readiness;
    private final PluginDescriptor descriptor;

    public FixturePlugin(DatasetDefinition definition) {
        definition = Objects.requireNonNull(definition, "definition");
        if (!DATASET_KEY.equals(definition.datasetKey())) {
            throw new IllegalArgumentException("definition must be fixture_daily");
        }
        readiness = new PluginReadiness(true, true, false, UNAVAILABLE);
        ApiDescriptor api = new ApiDescriptor(
                definition.datasetKey().apiName(),
                definition.displayName(),
                definition.category(),
                definition.queryMode(),
                definition.parameters());
        descriptor = new PluginDescriptor(
                DATASET_KEY.pluginId(),
                "Fixture",
                "Fixture 验收数据源",
                readiness.enabled(),
                readiness.credentialConfigured(),
                readiness.downloadAvailable(),
                readiness.unavailableReason(),
                List.of(api),
                List.of(DATASET_KEY));
    }

    @Override
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public PluginReadiness readiness() {
        return readiness;
    }

    @Override
    public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        if (!DATASET_KEY.apiName().equals(apiName)) {
            throw new IllegalArgumentException("Unknown Fixture API");
        }
        throw new SourceException(ErrorCode.SOURCE_UNAVAILABLE, UNAVAILABLE);
    }
}
```

Do not add getters, overloads, scenario parsing, factory interfaces, logging, or I/O.

- [ ] **Step 7: Implement the minimal conditional configuration**

Create `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`:

```java
package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("acceptance")
@ConditionalOnProperty(
        prefix = "tensor.plugins.fixture",
        name = "enabled",
        havingValue = "true")
public final class FixtureConfiguration {
    private static final DatasetDefinition DEFINITION = definition();

    @Bean
    public FixturePlugin fixturePlugin() {
        return new FixturePlugin(DEFINITION);
    }

    @Bean
    public DatasetAdapter fixtureDatasetAdapter() {
        return new GenericDatasetAdapter(DEFINITION, new ValueConverter(), new FingerprintKeyCodec());
    }

    private static DatasetDefinition definition() {
        DatasetKey key = DatasetKey.of(PluginId.of("fixture"), ApiName.of("fixture_daily"));
        return new DatasetDefinition(
                key,
                "Fixture 日线",
                "验收",
                QueryMode.trade_date,
                List.of(new ParameterDescriptor(
                        "scenario",
                        "场景",
                        "确定性验收场景",
                        ParameterType.ENUM,
                        true,
                        "SUCCESS",
                        List.of("SUCCESS", "EMPTY", "SOURCE_FAILURE", "TYPE_FAILURE", "PERSISTENCE_FAILURE"),
                        null,
                        null)),
                TableName.from(key),
                List.of(
                        column("ts_code", LogicalType.STRING, false, 0, 64, null, null),
                        column("trade_date", LogicalType.DATE, false, 1, null, null, null),
                        column("amount", LogicalType.DECIMAL, false, 2, null, 38, 18),
                        column("note", LogicalType.STRING, true, 3, 255, null, null)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(new FilterDefinition("ts_code")),
                "ts_code");
    }

    private static ColumnDefinition column(
            String name,
            LogicalType type,
            boolean nullable,
            int order,
            Integer length,
            Integer precision,
            Integer scale) {
        return new ColumnDefinition(
                name, name, type, nullable, order, length, precision, scale, List.of(), false);
    }
}
```

Keep `DEFINITION`, `definition()`, and `column(...)` private. Do not expose definition/converter/codec Beans and do not read YAML at runtime.

- [ ] **Step 8: Run the focused GREEN and inspect the exact count**

Run the Step 5 command unchanged.

Expected: exit 0, `FixturePluginTest` 6/6, no failures/errors/skips. Confirm the enabled context uses `GenericDatasetAdapter` and the inactive contexts contain neither fixture Bean.

- [ ] **Step 9: Run module and complete reactor gates**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

Expected: first command 160/160 (79 plugin-api + 75 core + 6 fixture). Second and third commands 266/266 (79 + 75 + 93 + 6 + 13), with zero failures/errors/skips, six Enforcer executions, and the adjusted ArchUnit test passing. Rerun unchanged outside a restricted JVM if Mockito attach alone is blocked.

- [ ] **Step 10: Run dependency, JAR, static, range, and format gates**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am dependency:tree \
  -Dincludes=com.akkc.tensor:tensor-core,org.springframework.boot:spring-boot-autoconfigure
jar tf data-plane/tensor-plugin-fixture/target/tensor-plugin-fixture-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/plugin/fixture/(FixturePlugin|FixtureConfiguration)|datasets/fixture/fixture_daily.yaml'
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/plugin/fixture|datasets/fixture'
rg -n 'plugin\.tushare|tensor-plugin-tushare|RestClient|ServiceLoader|java\.sql|javax\.sql|JdbcTemplate|Flyway' \
  data-plane/tensor-plugin-fixture/pom.xml data-plane/tensor-plugin-fixture/src
rg -n '@Profile\("acceptance"\)|tensor\.plugins\.fixture|ConditionalOnProperty|GenericDatasetAdapter' \
  data-plane/tensor-plugin-fixture/src/main/java data-plane/tensor-plugin-fixture/src/test/java
git diff --quiet -- \
  data-plane/pom.xml data-plane/tensor-plugin-api data-plane/tensor-core \
  data-plane/tensor-plugin-tushare data-plane/tensor-app/pom.xml data-plane/tensor-app/src/main
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java
git diff --check
```

Expected:

- dependency tree resolves the two new direct dependencies in compile scope and no fixture dependency on Tushare/app;
- fixture JAR output includes both class paths and the YAML; app JAR scan has no output and exits 1;
- prohibited capability scan has no output and exits 1; authorization scan finds profile, property, condition, and `GenericDatasetAdapter`;
- protected-path diff and reactor clean exit 0;
- scoped status after clean lists exactly two modified files and four new files from this task, with no `target/`;
- `git diff --check` exits 0.

- [ ] **Step 11: Commit the exact implementation scope**

Run:

```bash
git add \
  data-plane/tensor-plugin-fixture/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java \
  data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java \
  data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java \
  data-plane/tensor-plugin-fixture/src/main/resources/datasets/fixture/fixture_daily.yaml \
  data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java
git commit -m "feat(fixture): add acceptance data-source plugin"
```

Expected: one commit with exactly six files, two modified and four created.

- [ ] **Step 12: Verify the committed state and clean generated output**

Run a fresh committed-state gate:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
git show --stat --oneline --summary HEAD
git diff --check HEAD^ HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
```

Expected: fresh 266/266 reactor verification passes; the commit subject and exact six-file scope match Step 11; diff check and clean pass; final status has no output. Record the RED cause, focused 6/6, module 160/160, final 266/266, Enforcer/ArchUnit, dependency/JAR/static/range/format/clean results for the later `IN_PROGRESS -> COMPLETED` evidence. Do not update task-board completion until an independent review confirms the implementation satisfies `docs/task-designs/M08-T01-design.md`.
