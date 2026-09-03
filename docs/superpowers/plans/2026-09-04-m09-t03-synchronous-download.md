# M09-T03 Synchronous Download API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `POST /api/v1/downloads` as a synchronous, metadata-driven flow that validates before upstream access, persists non-empty batches atomically, and returns success only after commit.

**Architecture:** A framework-free `DownloadService` in `tensor-core` composes immutable registries, parameter validation, one plugin call, one adapter call, and `PersistenceService` with an injected `Clock`. A Servlet-only `DownloadController` reads the request ID established by `RequestIdFilter`, maps two exact REST records, and delegates all semantics to the service. One explicit `DownloadControllerIT` manually wires the acceptance fixture and MySQL 8.4.6; production Bean assembly remains M09-T06.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring MVC, Jakarta Bean Validation, Spring JDBC/Transactions, JUnit 5, AssertJ, Mockito, MockMvc, Flyway, Testcontainers MySQL 8.4.6.

**Spec:** `docs/task-designs/M09-T03-design.md`

## Global Constraints

- The authoritative design is `docs/task-designs/M09-T03-design.md`; do not broaden its five-file implementation scope.
- Create exactly four production Java files and one `DownloadControllerIT.java`; do not modify POMs, contracts, migrations, metadata, existing Java, or test lifecycle configuration.
- Use strict TDD: create the complete IT first, observe a `testCompile` RED caused only by the four missing production types, then add production code.
- Keep parameter validation, plugin download, and adaptation outside database transactions; reject an already-active transaction before upstream work.
- An empty successful envelope must not read the clock, adapt, or persist.
- A non-empty success response may be constructed only after `PersistenceService.persist` returns from its committed transaction.
- Preserve parameter/source/adapter exceptions and Spring database causes; use no catch-all wrapper.
- Use the injected `Clock` exactly once for each non-empty batch; never call `Instant.now()` or system time directly.
- Use no Tushare/fixture branches, Token access, retry, async execution, progress, history, cancellation, or production test hook.
- Standard error JSON/HTTP mapping and the production Bean graph remain M09-T05 and M09-T06 respectively.
- The implementation commit must contain exactly the five implementation files and use `feat(api): execute synchronous dataset downloads`.

---

### Task 1: Implement the M09-T03 synchronous download boundary

**Files:**
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java`

**Interfaces:**
- Consumes: `PluginRegistry.find(PluginId)`, `PluginRegistry.descriptors()`, `AdapterRegistry.find(DatasetKey)`, `ParameterValidator.validate(ApiDescriptor, Map<String,Object>)`, `DataSourcePlugin.download(ApiName, Map<String,Object>)`, `DatasetAdapter.adapt(DownloadEnvelope, Instant)`, `PersistenceService.persist(AdaptedBatch)`, and `RequestIdFilter.MDC_KEY`.
- Produces: `DownloadService.execute(PluginId, ApiName, Map<String,Object>, RequestId) -> DownloadResult`, `POST /api/v1/downloads`, `DownloadRequest(pluginId, apiName, params)`, and `DownloadResponse.from(DownloadResult)`.

- [ ] **Step 1: Re-read the authoritative inputs and confirm a clean baseline**

Read, in order:

```text
docs/task-designs/M09-T03-design.md
docs/task-handoffs/M09-T03-handoff.md
docs/task-handoffs/tensor-v1-task-board.md (M09-T03 row and detail)
docs/superpowers/plans/tensor-modules/M09-app-api.md (Global Constraints, Task M09-T03, Module Gate)
docs/contracts/openapi-v1.yaml (/api/v1/downloads, DownloadRequest, DownloadResponse)
docs/task-designs/M05-T01-design.md
docs/task-designs/M05-T03-design.md
docs/task-designs/M05-T05-design.md
docs/task-designs/M06-T04-design.md
docs/task-designs/M07-T04-design.md
docs/task-designs/M09-T01-design.md
```

Run:

```bash
git status --short
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

Expected: Git status is empty; Maven reports plugin-api 79, core 75, Tushare 93, fixture 12, app 36, total 295/295, with zero failures/errors/skips and all six Enforcer executions successful.

- [ ] **Step 2: Create the complete integration contract before production code**

Create `DownloadControllerIT.java` with the constants and container configuration below, then add exactly the ten ordinary `@Test` methods named in the numbered contract that follows:

```java
private static final String TABLE = "fixture__fixture_daily";
private static final String REQUEST_ID = "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";
private static final PluginId PLUGIN_ID = PluginId.of("fixture");
private static final ApiName API_NAME = ApiName.of("fixture_daily");
private static final DatasetKey DATASET_KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
private static final Instant INGESTED_AT = Instant.parse("2026-08-07T08:09:10.123Z");
private static final Clock FIXED_CLOCK = Clock.fixed(INGESTED_AT, ZoneOffset.UTC);
private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.4.6"))
        .withDatabaseName("tensor")
        .withUsername("tensor")
        .withPassword("tensor")
        .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs");
```

1. `exposesExactSurfacesAndImmutableDtos`: assert `DownloadService` and `DownloadController` are final; assert their sole public constructors have the design parameter types; assert `DownloadService` declares only public `execute(PluginId, ApiName, Map, RequestId)` and Controller only public `download(DownloadRequest)`. Validate a `DownloadRequest` with `LocalValidatorFactoryBean`, prove `pluginId/apiName/params` constraints, mutate the source `LinkedHashMap`, and prove the record retained an ordered immutable snapshot. Construct both valid `EMPTY` and `SUCCESS` `DownloadResult` values, map them with `DownloadResponse.from`, assert exact eight component names and constructor invariants.
2. `rejectsInvalidRequestAndParametersBeforeDownload`: use standalone MockMvc with a mocked `DownloadService` to POST uppercase `pluginId` and null `params`; assert HTTP 400 and `verifyNoInteractions(service)`. Then use a real `DownloadService` around a test plugin whose one required TEXT parameter has no default; an empty map must throw `ParameterValidationException` with `PARAM_REQUIRED`. Execute the real fixture wrapper with `scenario="UNKNOWN"` and `scenario=1`; both must throw `PARAM_INVALID`. In all semantic cases the counting plugin download count, adapter count, and database row count remain zero.
3. `rejectsUnavailablePluginAndMisconfiguredDatasetBeforeDownload`: construct a real `PluginRegistry` around a Mockito plugin with unavailable readiness and assert `PLUGIN_DISABLED`, fixed message, and no download. With a real ready fixture, execute an unknown API and then execute with an empty `AdapterRegistry`; both assert `DATASET_MISCONFIGURED`, fixed message, zero download, and zero rows.
4. `preservesSourceFailureAndRejectsInvalidEnvelopesBeforeAdaptation`: execute fixture `SOURCE_FAILURE` and assert the thrown `SourceException` is `SOURCE_UNAVAILABLE` with `Fixture source unavailable`. Use a test plugin that returns (a) a `FAILURE` envelope, (b) null, and (c) a zero-row successful envelope with a different plugin identity. Assert `SOURCE_PAYLOAD_INVALID`; case (a) preserves the safe envelope error, cases (b)/(c) use `Source returned an invalid payload`; adapter/persistence remain untouched.
5. `returnsEmptyResponseWithoutClockAdaptationOrPersistence`: wire the real fixture through counting wrappers with mocked `Clock` and `PersistenceService`, POST `scenario=EMPTY` through real `RequestIdFilter` and Controller, and assert HTTP 200, exact header/body request ID, exact eight-field JSON order, `EMPTY`, three zeros, and `下载成功，0 条数据`. Assert one plugin call, zero adapter calls, and no interactions with clock/persistence.
6. `persistsSuccessBeforeReturningExactResponse`: POST `scenario=SUCCESS` through the fully real fixture/registry/validator/adapter/persistence/MySQL flow and fixed clock. Assert HTTP 200 and exact ordered JSON values `SUCCESS`, source 1, inserted 1, updated 0, `下载成功`; query the table after the response and assert the one row contains `000001.SZ`, `2026-08-07`, exact `11.230000000000000000`, null note, source `fixture/fixture_daily`, and `INGESTED_AT`.
7. `reportsAnUpdateForARepeatedUniqueFixtureRow`: issue two `SUCCESS` requests against one real service. Assert the first counts `(1,1,0)`, the second `(1,0,1)`, both satisfy `sourceRowCount == insertedRows + updatedRows`, and the table contains one row.
8. `stopsAdapterFailureBeforeDatabaseAccess`: execute fixture `TYPE_FAILURE`; assert the exact `AdapterException` code/message `ADAPTER_TYPE_INVALID` / `Invalid adapter value: api=fixture_daily, row=0, field=amount`, and assert zero rows.
9. `rollsBackPersistenceFailureWithoutReturningSuccess`: seed one committed `SUCCESS`; create `BEFORE UPDATE` trigger `m09_t03_fail` with `SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fixture persistence failure'`; execute `PERSISTENCE_FAILURE` and assert `DataAccessException` with that root message. Drop the trigger in `finally`, then query and assert one unchanged seed row with null note, original amount, and original `ingested_at`.
10. `rejectsAnOuterTransactionBeforeUpstreamWork`: wrap `service.execute` in a `TransactionTemplate` using the same `DataSourceTransactionManager`; assert fixed `IllegalStateException("Download orchestration must not run in a transaction")`, zero plugin calls, and zero rows.

Use these lifecycle/helper interfaces, implemented entirely in the test file:

```java
@BeforeAll
static void startEnvironment() {
    MYSQL.start();
    dataSource = new DriverManagerDataSource(
            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway flyway = Flyway.configure().dataSource(dataSource)
            .locations("classpath:db/migration").load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    fixtureContext = new AnnotationConfigApplicationContext();
    fixtureContext.getEnvironment().setActiveProfiles("acceptance");
    fixtureContext.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("m09-t03", Map.of("tensor.plugins.fixture.enabled", "true")));
    fixtureContext.register(FixtureConfiguration.class);
    fixtureContext.refresh();
    fixturePlugin = fixtureContext.getBean(DataSourcePlugin.class);
    fixtureAdapter = fixtureContext.getBean(DatasetAdapter.class);
    beanValidator = new LocalValidatorFactoryBean();
    beanValidator.afterPropertiesSet();
}

@BeforeEach
void clearDatabase() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP TRIGGER IF EXISTS m09_t03_fail");
    jdbc.update("DELETE FROM " + TABLE);
    MDC.clear();
}

@AfterAll
static void stopEnvironment() {
    if (beanValidator != null) beanValidator.close();
    if (fixtureContext != null) fixtureContext.close();
    MYSQL.stop();
}

private static DownloadService realService(
        DataSourcePlugin plugin, DatasetAdapter adapter, Clock clock) {
    DatasetCatalog catalog = new DatasetStartupValidator(
            List.of(adapter.definition()), new SchemaInspector(dataSource)).validate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    return new DownloadService(
            new PluginRegistry(List.of(plugin)),
            new AdapterRegistry(List.of(adapter)),
            new ParameterValidator(),
            new PersistenceService(
                    catalog,
                    new DatasetLockManager(),
                    new ExistingKeyRepository(jdbc),
                    new GenericUpsertRepository(jdbc),
                    new DataSourceTransactionManager(dataSource)),
            clock);
}

private static MockMvc mockMvc(DownloadService service) {
    return MockMvcBuilders.standaloneSetup(new DownloadController(service))
            .setValidator(beanValidator)
            .addFilters(new RequestIdFilter())
            .build();
}
```

Add a private `CountingPlugin` delegating `descriptor/readiness/download` and incrementing only `download`; add a private `CountingAdapter` delegating `datasetKey/definition/adapt` and incrementing only `adapt`. Add literal helper factories for a ready single-API `PluginDescriptor`, a required-no-default `ApiDescriptor`, a failure envelope, and an identity-mismatch empty envelope. Expected values must be literal or from public record accessors; do not call production private helpers or recreate orchestration in test code.

Use these exact wrapper methods so stage-count assertions observe real SPI behavior:

```java
private static final class CountingPlugin implements DataSourcePlugin {
    private final DataSourcePlugin delegate;
    private int downloads;

    private CountingPlugin(DataSourcePlugin delegate) {
        this.delegate = delegate;
    }

    @Override public PluginDescriptor descriptor() { return delegate.descriptor(); }
    @Override public PluginReadiness readiness() { return delegate.readiness(); }
    @Override public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
        downloads++;
        return delegate.download(apiName, params);
    }
}

private static final class CountingAdapter implements DatasetAdapter {
    private final DatasetAdapter delegate;
    private int adaptations;

    private CountingAdapter(DatasetAdapter delegate) {
        this.delegate = delegate;
    }

    @Override public DatasetKey datasetKey() { return delegate.datasetKey(); }
    @Override public DatasetDefinition definition() { return delegate.definition(); }
    @Override public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) {
        adaptations++;
        return delegate.adapt(envelope, ingestedAt);
    }
}
```

- [ ] **Step 3: Run the test-only state and verify the strict RED**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DownloadControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-zero at `tensor-app:testCompile`; diagnostics identify only missing `DownloadService`, `DownloadController`, `DownloadRequest`, and `DownloadResponse`. Fix test imports/syntax before continuing if any other failure appears. Save the complete output as `/private/tmp/m09-t03-red.log` for review, without adding it to Git.

- [ ] **Step 4: Implement the minimal core orchestration**

Create `DownloadService.java` with this exact public surface and flow:

```java
package com.akkc.tensor.core.download;

import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.validation.ValidatedParameters;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class DownloadService {
    private final PluginRegistry pluginRegistry;
    private final AdapterRegistry adapterRegistry;
    private final ParameterValidator parameterValidator;
    private final PersistenceService persistenceService;
    private final Clock clock;

    public DownloadService(
            PluginRegistry pluginRegistry,
            AdapterRegistry adapterRegistry,
            ParameterValidator parameterValidator,
            PersistenceService persistenceService,
            Clock clock) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.parameterValidator = Objects.requireNonNull(parameterValidator, "parameterValidator");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DownloadResult execute(
            PluginId pluginId,
            ApiName apiName,
            Map<String, Object> params,
            RequestId requestId) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(requestId, "requestId");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Download orchestration must not run in a transaction");
        }

        DataSourcePlugin plugin = pluginRegistry.find(pluginId)
                .orElseThrow(() -> access(ErrorCode.PLUGIN_DISABLED));
        ApiDescriptor api = descriptor(pluginId).apis().stream()
                .filter(candidate -> candidate.apiName().equals(apiName))
                .findFirst()
                .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        DatasetKey key = DatasetKey.of(pluginId, apiName);
        DatasetAdapter adapter = adapterRegistry.find(key)
                .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        ValidatedParameters validated = parameterValidator.validate(api, params);
        DownloadEnvelope envelope = plugin.download(apiName, validated.values());
        if (envelope == null) {
            throw invalidPayload();
        }
        if (envelope.status() == DownloadStatus.FAILURE) {
            throw new SourceException(ErrorCode.SOURCE_PAYLOAD_INVALID, envelope.error());
        }
        if (!envelope.pluginId().equals(pluginId)
                || !envelope.apiName().equals(apiName)
                || !envelope.params().equals(validated.values())) {
            throw invalidPayload();
        }
        if (envelope.rowCount() == 0) {
            return new DownloadResult(
                    requestId, DownloadOutcome.EMPTY, pluginId, apiName, 0, 0, 0,
                    "下载成功，0 条数据");
        }

        AdaptedBatch batch = adapter.adapt(envelope, clock.instant());
        WriteCounts counts = persistenceService.persist(batch);
        return new DownloadResult(
                requestId,
                DownloadOutcome.SUCCESS,
                pluginId,
                apiName,
                envelope.rowCount(),
                counts.insertedRows(),
                counts.updatedRows(),
                "下载成功");
    }

    private PluginDescriptor descriptor(PluginId pluginId) {
        List<PluginDescriptor> matches = pluginRegistry.descriptors().stream()
                .filter(candidate -> candidate.pluginId().equals(pluginId))
                .filter(PluginDescriptor::downloadAvailable)
                .toList();
        if (matches.size() != 1) {
            throw access(ErrorCode.DATASET_MISCONFIGURED);
        }
        return matches.get(0);
    }

    private static DownloadAccessException access(ErrorCode code) {
        return new DownloadAccessException(
                code,
                code == ErrorCode.PLUGIN_DISABLED
                        ? "Download plugin is unavailable"
                        : "Download dataset is unavailable");
    }

    private static SourceException invalidPayload() {
        return new SourceException(
                ErrorCode.SOURCE_PAYLOAD_INVALID, "Source returned an invalid payload");
    }

    private static final class DownloadAccessException extends TensorException {
        private DownloadAccessException(ErrorCode code, String message) {
            super(requireAccessCode(code), message);
        }

        private static ErrorCode requireAccessCode(ErrorCode code) {
            if (code != ErrorCode.PLUGIN_DISABLED && code != ErrorCode.DATASET_MISCONFIGURED) {
                throw new IllegalArgumentException("Unsupported download access error code");
            }
            return code;
        }
    }
}
```

Do not annotate the service, catch database exceptions, or add a command object. Do not run the app IT yet because the three app production types are still absent.

- [ ] **Step 5: Implement immutable request and response records**

Create `DownloadRequest.java`:

```java
package com.akkc.tensor.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DownloadRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String pluginId,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String apiName,
        @NotNull Map<String, Object> params) {
    public DownloadRequest {
        if (params != null) {
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }
}
```

Create `DownloadResponse.java`:

```java
package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import java.util.Objects;

public record DownloadResponse(
        String requestId,
        DownloadOutcome outcome,
        String pluginId,
        String apiName,
        long sourceRowCount,
        long insertedRows,
        long updatedRows,
        String message) {
    public DownloadResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(message, "message");
        if (requestId.isBlank() || pluginId.isBlank() || apiName.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("download response text must not be blank");
        }
        if (sourceRowCount < 0 || insertedRows < 0 || updatedRows < 0) {
            throw new IllegalArgumentException("row counts must be non-negative");
        }
        if (outcome == DownloadOutcome.EMPTY
                && (sourceRowCount != 0 || insertedRows != 0 || updatedRows != 0)) {
            throw new IllegalArgumentException("empty response counts must all be zero");
        }
        if (outcome == DownloadOutcome.SUCCESS && sourceRowCount == 0) {
            throw new IllegalArgumentException("successful response must contain source rows");
        }
    }

    public static DownloadResponse from(DownloadResult result) {
        Objects.requireNonNull(result, "result");
        return new DownloadResponse(
                result.requestId().value().toString(),
                result.outcome(),
                result.pluginId().value(),
                result.apiName().value(),
                result.sourceRowCount(),
                result.insertedRows(),
                result.updatedRows(),
                result.message());
    }
}
```

- [ ] **Step 6: Implement the thin Servlet controller**

Create `DownloadController.java`:

```java
package com.akkc.tensor.web;

import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.web.dto.DownloadResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/downloads")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DownloadController {
    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = Objects.requireNonNull(downloadService, "downloadService");
    }

    @PostMapping
    public DownloadResponse download(@Valid @RequestBody DownloadRequest request) {
        Objects.requireNonNull(request, "request");
        String value = MDC.get(RequestIdFilter.MDC_KEY);
        if (value == null) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        return DownloadResponse.from(downloadService.execute(
                PluginId.of(request.pluginId()),
                ApiName.of(request.apiName()),
                request.params(),
                new RequestId(UUID.fromString(value))));
    }
}
```

Do not add `@ExceptionHandler`, `@Transactional`, field injection, a second request ID, or a Spring configuration class.

- [ ] **Step 7: Run the fixed MySQL GREEN and correct only implementation defects**

Run:

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DownloadControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `DownloadControllerIT` 10/10, zero failures/errors/skips. If compilation or assertions fail, change only the five task files; preserve every frozen interface, message, stage order, and fixture/MySQL requirement. Save the successful output as `/private/tmp/m09-t03-green.log`.

- [ ] **Step 8: Run the existing and new integration flows together**

Run:

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DownloadControllerIT,FixtureFlowIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 15/15, zero failures/errors/skips; both classes use MySQL 8.4.6 and complete without skipped containers.

- [ ] **Step 9: Prove the three stage-order tests with controlled mutations**

Perform each mutation separately, run the focused IT, capture the expected failure, and restore the production file before the next mutation:

```text
Mutation A: move parameterValidator.validate below plugin.download.
Expected: rejectsInvalidRequestAndParametersBeforeDownload fails because download count becomes non-zero.

Mutation B: delete the rowCount == 0 return branch.
Expected: returnsEmptyResponseWithoutClockAdaptationOrPersistence fails on clock/adapter/persistence interaction.

Mutation C: return SUCCESS before persistenceService.persist, or omit that call.
Expected: persistsSuccessBeforeReturningExactResponse and rollback coverage fail because no committed row/exception exists.
```

After restoring, rerun Step 7 and expect 10/10. Save only logs in `/private/tmp/m09-t03-mutation-{validation,empty,persist}.log`; never stage them.

- [ ] **Step 10: Run reactor and static/package gates**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

Expected: both commands report 295/295, zero failures/errors/skips; all six Enforcer executions, app ArchUnit, and forbidden-Git tests pass. The new `*IT` compiles but does not change Surefire discovery.

Run:

```bash
rg -n 'TransactionSynchronizationManager|PluginRegistry|AdapterRegistry|ParameterValidator|plugin\.download|adapter\.adapt|persistenceService\.persist|clock\.instant|DownloadOutcome\.(SUCCESS|EMPTY)' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java
rg -n '@RestController|/api/v1/downloads|@Valid|RequestIdFilter\.MDC_KEY|DownloadResponse\.from' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java
rg -n 'Tushare|Fixture|RestClient|JdbcTemplate|DataSource|Thread|CompletableFuture|Executor|Retry|Authorization|Cookie|(?i:token|credential)|System\.currentTimeMillis|Instant\.now' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java
jar tf data-plane/tensor-core/target/tensor-core-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/core/download/DownloadService.*\.class'
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/web/(DownloadController|dto/DownloadRequest|dto/DownloadResponse).*\.class'
git diff --check
git status --short --untracked-files=all -- data-plane
```

Expected: authorized scans show only the designed mechanisms; the forbidden scan has no output and exits 1; JAR scans show exactly the new production classes/nested access exception; format passes; scoped status shows only the five new Java files and no `target/` after the later clean step.

- [ ] **Step 11: Verify protected paths and stage exactly the implementation**

Run:

```bash
git diff --quiet -- \
  docs data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DataSourceResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DatasetDefinitionResponse.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short --untracked-files=all -- data-plane
```

Expected: protected paths and format pass; clean succeeds; status lists exactly the five new Java files. Then stage only:

```bash
git add \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java
git diff --cached --check
git diff --cached --name-only
```

Expected: cached name list is exactly those five paths.

- [ ] **Step 12: Commit and re-verify the committed state**

Commit:

```bash
git commit -m "feat(api): execute synchronous dataset downloads"
```

Run the Step 7 focused IT, Step 8 combined IT, and Step 10 reactor `verify` again on the commit. Then run:

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-status HEAD
git diff --check HEAD^ HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
rg -n 'm09-t03-secret-sentinel|unknown_api' \
  /private/tmp/m09-t03-red.log \
  /private/tmp/m09-t03-green.log \
  /private/tmp/m09-t03-mutation-validation.log \
  /private/tmp/m09-t03-mutation-empty.log \
  /private/tmp/m09-t03-mutation-persist.log
```

Expected: commit message and five-file range are exact; all fresh tests/gates retain their expected counts; clean succeeds; working tree is empty; the final log scan has no output and exits 1. Delete the five `/private/tmp/m09-t03-*.log` files after recording the non-sensitive command results in the task completion evidence.
