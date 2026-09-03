# M09-T04 Read-Only Dataset Paging API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the read-only dataset records endpoint with catalog-first validation, stable server paging, and lossless DECIMAL/BIGINT JSON strings while pagination controls remain JSON numbers.

**Architecture:** A Servlet-only `DatasetController` checks the immutable `DatasetCatalog`, maps the seven fixed HTTP query inputs to `QueryCriteria`, then delegates all SQL, count, stable ordering, and page normalization to the existing `DatasetQueryService`. `PageResponse` reuses `DatasetPage` invariants, while one Boot Jackson module serializes boxed `Long` and `BigDecimal` values as plain strings without changing primitive page controls. One explicit `DatasetControllerIT` manually wires the real catalog/query stack and MySQL 8.4.6; production Bean assembly remains M09-T06.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring MVC, Jackson, Spring JDBC, JUnit 5, AssertJ, MockMvc, Testcontainers MySQL 8.4.6.

**Spec:** `docs/task-designs/M09-T04-design.md`

## Global Constraints

- The authoritative design is `docs/task-designs/M09-T04-design.md`; do not broaden its four-file implementation scope.
- Create exactly three production Java files and one `DatasetControllerIT.java`; do not modify POMs, contracts, migrations, metadata, Core/plugin code, existing app code, or test lifecycle configuration.
- Use strict TDD: create the complete IT first, observe a `testCompile` RED caused only by the three missing production types, then add production code.
- The only route is `GET /api/v1/data-sources/{pluginId}/datasets/{apiName}/records`; add no records POST/PUT/PATCH/DELETE mapping.
- Inject both `DatasetCatalog` and `DatasetQueryService`; locate and validate catalog metadata before constructing criteria or calling the query service.
- Accept only `tsCode`, `tradeDateFrom/To`, `annDateFrom/To`, `page`, and `pageSize`; default page/pageSize to 1/50 and do not accept client table, column, sort, operator, or SQL values.
- Preserve the Core `DatasetPage` result; do not recalculate totals, normalize pages, reorder columns/items, query JDBC directly, or convert Core values in the DTO.
- Serialize boxed `Long` and `BigDecimal` as strings, using `BigDecimal.toPlainString()`; never register the string serializer for primitive `long.class`.
- Keep standard error JSON/complete HTTP mapping and production Bean assembly in M09-T05/M09-T06 respectively.
- The implementation commit must contain exactly the four implementation files and use `feat(api): expose read-only dataset paging`.

---

### Task 1: Implement the M09-T04 read-only paging boundary

**Files:**
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java`

**Interfaces:**
- Consumes: `DatasetCatalog.find(DatasetKey)`, `DatasetQueryService.query(DatasetKey, QueryCriteria)`, `DatasetPage(columns, items, page, pageSize, totalElements, totalPages)`, and `RequestIdFilter.MDC_KEY`.
- Produces: `GET /api/v1/data-sources/{pluginId}/datasets/{apiName}/records`, `DatasetController(DatasetCatalog, DatasetQueryService)`, `PageResponse.from(String, DatasetKey, DatasetPage)`, and the Boot `Module precisionModule()` Bean.

- [ ] **Step 1: Re-read the authoritative inputs and confirm a clean baseline**

Read, in order:

```text
docs/task-designs/M09-T04-design.md
docs/task-handoffs/M09-T04-handoff.md
docs/task-handoffs/tensor-v1-task-board.md (M09-T04 row and detail)
docs/superpowers/plans/tensor-modules/M09-app-api.md (Global Constraints, Task M09-T04, Module Gate)
docs/contracts/openapi-v1.yaml (/api/v1/data-sources/{pluginId}/datasets/{apiName}/records and PageResponse)
docs/task-designs/M05-T02-design.md
docs/task-designs/M06-T06-design.md
docs/task-designs/M09-T01-design.md
data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java
data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QueryCriteria.java
data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/DatasetPage.java
data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/DatasetQueryService.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java
```

Run:

```bash
git status --short
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

Expected: Git status is empty; Maven reports plugin-api 79, core 75, Tushare 93, fixture 12, app 36, total 295/295, with zero failures/errors/skips and all six Enforcer executions, app ArchUnit, and forbidden-Git tests successful.

- [ ] **Step 2: Create the complete integration contract before production code**

Create `DatasetControllerIT.java` with these fixed identities and MySQL environment:

```java
private static final String TABLE = "fixture__query_records";
private static final String REQUEST_ID = "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";
private static final PluginId PLUGIN_ID = PluginId.of("fixture");
private static final ApiName API_NAME = ApiName.of("query_records");
private static final DatasetKey DATASET_KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
private static final Instant INGESTED_AT = Instant.parse("2026-08-07T08:09:10.123Z");
private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.4.6"))
        .withDatabaseName("tensor")
        .withUsername("tensor")
        .withPassword("tensor")
        .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_0900_as_cs");

private static DriverManagerDataSource rawDataSource;

@BeforeAll
static void startEnvironment() {
    MYSQL.start();
    rawDataSource = new DriverManagerDataSource(
            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    new JdbcTemplate(rawDataSource).execute("""
            CREATE TABLE fixture__query_records (
                ts_code VARCHAR(64) NOT NULL,
                trade_date DATE NOT NULL,
                ann_date DATE NOT NULL,
                amount DECIMAL(38,18) NOT NULL,
                volume BIGINT NOT NULL,
                note VARCHAR(255) NULL,
                source_plugin VARCHAR(64) NOT NULL,
                source_api VARCHAR(64) NOT NULL,
                ingested_at DATETIME(3) NOT NULL,
                PRIMARY KEY (ts_code, trade_date, ann_date)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs
            """);
}

@BeforeEach
void clearTableAndMdc() {
    new JdbcTemplate(rawDataSource).update("DELETE FROM " + TABLE);
    MDC.clear();
}

@AfterAll
static void stopEnvironment() {
    MYSQL.stop();
}
```

Add exactly these eight ordinary `@Test` methods and assertions:

1. `exposesExactSurfacesAndImmutableDtos`: assert `DatasetController` and `JacksonPrecisionConfiguration` are final; Controller has one public constructor with `(DatasetCatalog, DatasetQueryService)` and one public method named `listDatasetRecords`; Controller has `@ConditionalOnWebApplication(SERVLET)`. Assert PageResponse record component names/types are exactly `requestId:String`, `pluginId:String`, `apiName:String`, `page:int`, `pageSize:int`, `totalElements:long`, `totalPages:long`, `columns:List`, `items:List`, with only public static `from(String, DatasetKey, DatasetPage)`. Mutate the source columns/map/items after construction and assert the record retains an ordered immutable snapshot; assert null/blank identities and invalid `DatasetPage` invariants fail. Register `precisionModule()` on an ObjectMapper with Java time support, serialize a page whose row contains `new BigDecimal("1E+3")` and `Long.MAX_VALUE`, then assert the values are text nodes `"1000"` and `"9223372036854775807"`, while page/pageSize/totalElements/totalPages are number nodes.
2. `returnsAnEmptyDefaultPage`: call the GET route without query parameters through real `RequestIdFilter`; assert HTTP 200, matching header/body request ID, exact nine top-level field order, `page=1`, `pageSize=50`, zero totals, exact six business columns followed by `source_plugin,source_api,ingested_at`, and empty items.
3. `supportsUnfilteredTwentyFiftyAndOneHundredRowPages`: insert 101 distinct rows; request page 1 with each pageSize 20, 50, and 100; assert totals 101 and 6/3/2 pages respectively, item counts 20/50/100, and ascending composite-key order.
4. `appliesAllDeclaredFiltersWithAndSemantics`: insert one full match and rows that differ independently in tsCode, trade date, or ann date; request all five filter values and assert only the full match is returned. Assert columns and row keys are in the same exact order and verify a request using only one date boundary still returns the appropriate rows.
5. `rejectsUnsupportedAndInvalidParametersBeforeDatabaseAccess`: build a flow whose definition declares only `ts_code`; request trade or ann filters and assert `400 + PARAM_INVALID`. On a full-filter flow, separately request an invalid tsCode, reversed trade range, reversed ann range, page 0, and pageSize 10; assert the same code/status. Request an invalid ISO date and a non-integer page and assert MVC 400. Reset the counting data source before each request and assert zero connections.
6. `rejectsMissingAndUnsafeDatasetMetadataBeforeDatabaseAccess`: use an empty catalog for the requested key and assert `409 + DATASET_MISCONFIGURED`; include `page=0` on the same request and prove catalog-first keeps 409. Then use a validated definition that declares only unsupported `note` and assert the same 409/code/fixed message. Reset observations and assert zero connections for each request.
7. `normalizesAnOutOfRangePageAndSerializesPreciseRows`: insert 23 ordered rows, with one last-page row containing `Long.MAX_VALUE`, `12345678901234567890.123456789012345678`, null note, and the fixed instant; request page 99/pageSize 20. Assert returned page 2, totalElements 23, totalPages 2, exactly 3 items, exact ordered row keys, precise values as JSON strings, ISO date/time strings, null preserved, and all four pagination controls as JSON numbers.
8. `doesNotExposeMutatingDatasetRoutes`: POST, PUT, PATCH, and DELETE the exact records path; assert 405 for each and zero counted connections.

Use this exact definition factory so the real startup validator and query service consume one schema contract:

```java
private static DatasetDefinition definition(String... filters) {
    return new DatasetDefinition(
            DATASET_KEY,
            "Query Records",
            "fixture",
            QueryMode.trade_date,
            List.of(),
            TableName.from(DATASET_KEY),
            List.of(
                    column("ts_code", LogicalType.STRING, false, 0, 64, null, null),
                    column("trade_date", LogicalType.DATE, false, 1, null, null, null),
                    column("ann_date", LogicalType.DATE, false, 2, null, null, null),
                    column("amount", LogicalType.DECIMAL, false, 3, null, 38, 18),
                    column("volume", LogicalType.LONG, false, 4, null, null, null),
                    column("note", LogicalType.STRING, true, 5, 255, null, null)),
            new BusinessKeyDefinition(
                    BusinessKeyMode.COMPOSITE,
                    List.of("ts_code", "trade_date", "ann_date")),
            Arrays.stream(filters).map(FilterDefinition::new).toList(),
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
```

Build every HTTP flow from a public validator and the same catalog instance supplied to both Controller and service:

```java
private static Flow flow(List<DatasetDefinition> definitions) {
    CountingDataSource dataSource = new CountingDataSource(rawDataSource);
    DatasetCatalog catalog = new DatasetStartupValidator(
            definitions, new SchemaInspector(dataSource)).validate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    DatasetQueryService queryService = new DatasetQueryService(
            catalog, new GenericQueryRepository(jdbc));
    ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .modulesToInstall(
                    new JavaTimeModule(),
                    new JacksonPrecisionConfiguration().precisionModule())
            .build();
    MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DatasetController(catalog, queryService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .addFilters(new RequestIdFilter())
            .build();
    dataSource.reset();
    return new Flow(mockMvc, objectMapper, dataSource);
}

private record Flow(
        MockMvc mockMvc,
        ObjectMapper objectMapper,
        CountingDataSource dataSource) {}
```

Use a delegating counter, without SQL interception or behavior changes:

```java
private static final class CountingDataSource implements DataSource {
    private final DataSource delegate;
    private final AtomicInteger connections = new AtomicInteger();

    private CountingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        connections.incrementAndGet();
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        connections.incrementAndGet();
        return delegate.getConnection(username, password);
    }

    @Override
    public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return delegate.createConnectionBuilder();
    }

    @Override
    public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return delegate.createShardingKeyBuilder();
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter writer) throws SQLException {
        delegate.setLogWriter(writer);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return delegate.unwrap(type);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        return delegate.isWrapperFor(type);
    }

    private void reset() {
        connections.set(0);
    }

    private int connectionCount() {
        return connections.get();
    }
}
```

Insert test rows with a literal parameterized statement, not production query helpers:

```java
private static void insert(
        String tsCode,
        LocalDate tradeDate,
        LocalDate annDate,
        BigDecimal amount,
        long volume,
        String note,
        Instant ingestedAt) {
    new JdbcTemplate(rawDataSource).update(
            "INSERT INTO fixture__query_records "
                    + "(ts_code,trade_date,ann_date,amount,volume,note,"
                    + "source_plugin,source_api,ingested_at) VALUES (?,?,?,?,?,?,?,?,?)",
            tsCode,
            java.sql.Date.valueOf(tradeDate),
            java.sql.Date.valueOf(annDate),
            amount,
            volume,
            note,
            "fixture",
            "query_records",
            java.sql.Timestamp.from(ingestedAt));
}
```

Assertions for controlled HTTP errors must inspect `MvcResult.getResolvedException()` as `TensorException`, then assert its `code()` and fixed message. Do not install a temporary `@ControllerAdvice` or assert a standard JSON error body before M09-T05.

- [ ] **Step 3: Run the test-only state and verify the strict RED**

Run:

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DatasetControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-zero at `tensor-app:testCompile`; diagnostics identify only missing `DatasetController`, `PageResponse`, and `JacksonPrecisionConfiguration`. Fix test imports/syntax before continuing if any other failure appears. Save the complete output as `/private/tmp/m09-t04-red.log` for review; do not add it to Git.

- [ ] **Step 4: Implement the immutable page response**

Create `PageResponse.java` exactly as follows:

```java
package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PageResponse(
        String requestId,
        String pluginId,
        String apiName,
        int page,
        int pageSize,
        long totalElements,
        long totalPages,
        List<String> columns,
        List<Map<String, Object>> items) {
    public PageResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        if (requestId.isBlank() || pluginId.isBlank() || apiName.isBlank()) {
            throw new IllegalArgumentException("page response identity must not be blank");
        }
        DatasetPage validated = new DatasetPage(
                columns, items, page, pageSize, totalElements, totalPages);
        columns = validated.columns();
        items = validated.items();
    }

    public static PageResponse from(
            String requestId, DatasetKey key, DatasetPage page) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(page, "page");
        return new PageResponse(
                requestId,
                key.pluginId().value(),
                key.apiName().value(),
                page.page(),
                page.pageSize(),
                page.totalElements(),
                page.totalPages(),
                page.columns(),
                page.items());
    }
}
```

Do not duplicate `DatasetPage` validation, stringify values, sort maps, or add Jackson annotations.

- [ ] **Step 5: Implement the boxed-number precision module**

Create `JacksonPrecisionConfiguration.java` exactly as follows:

```java
package com.akkc.tensor.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class JacksonPrecisionConfiguration {
    @Bean
    public Module precisionModule() {
        SimpleModule module = new SimpleModule("tensor-precision");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(
                    BigDecimal value,
                    JsonGenerator generator,
                    SerializerProvider serializers) throws IOException {
                generator.writeString(value.toPlainString());
            }
        });
        return module;
    }
}
```

Do not register `long.class`, `Integer`, or `Number`; do not replace Boot's `ObjectMapper` or change Java time serialization.

- [ ] **Step 6: Implement the catalog-first Servlet controller**

Create `DatasetController.java` exactly as follows:

```java
package com.akkc.tensor.web;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.PageResponse;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-sources")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DatasetController {
    private static final Set<String> SUPPORTED_FILTERS =
            Set.of("ts_code", "trade_date", "ann_date");

    private final DatasetCatalog datasetCatalog;
    private final DatasetQueryService datasetQueryService;

    public DatasetController(
            DatasetCatalog datasetCatalog,
            DatasetQueryService datasetQueryService) {
        this.datasetCatalog = Objects.requireNonNull(datasetCatalog, "datasetCatalog");
        this.datasetQueryService =
                Objects.requireNonNull(datasetQueryService, "datasetQueryService");
    }

    @GetMapping("/{pluginId}/datasets/{apiName}/records")
    public PageResponse listDatasetRecords(
            @PathVariable("pluginId") String pluginId,
            @PathVariable("apiName") String apiName,
            @RequestParam(value = "tsCode", required = false) String tsCode,
            @RequestParam(value = "tradeDateFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate tradeDateFrom,
            @RequestParam(value = "tradeDateTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate tradeDateTo,
            @RequestParam(value = "annDateFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate annDateFrom,
            @RequestParam(value = "annDateTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate annDateTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        DatasetKey key = key(pluginId, apiName);
        DatasetDefinition definition = datasetCatalog.find(key)
                .orElseThrow(DatasetQueryAccessException::new);
        Set<String> filters = definition.filters().stream()
                .map(filter -> filter.field())
                .collect(Collectors.toUnmodifiableSet());
        if (!SUPPORTED_FILTERS.containsAll(filters)) {
            throw new DatasetQueryAccessException();
        }
        if ((tsCode != null && !filters.contains("ts_code"))
                || ((tradeDateFrom != null || tradeDateTo != null)
                        && !filters.contains("trade_date"))
                || ((annDateFrom != null || annDateTo != null)
                        && !filters.contains("ann_date"))) {
            throw new InvalidQueryException();
        }

        QueryCriteria criteria;
        try {
            criteria = new QueryCriteria(
                    tsCode,
                    tradeDateFrom,
                    tradeDateTo,
                    annDateFrom,
                    annDateTo,
                    page,
                    pageSize);
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryException();
        }

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId == null) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        DatasetPage result;
        try {
            result = datasetQueryService.query(key, criteria);
        } catch (IllegalArgumentException exception) {
            throw new DatasetQueryAccessException();
        }
        return PageResponse.from(requestId, key, result);
    }

    private static DatasetKey key(String pluginId, String apiName) {
        try {
            return DatasetKey.of(PluginId.of(pluginId), ApiName.of(apiName));
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryException();
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private static final class InvalidQueryException extends TensorException {
        private InvalidQueryException() {
            super(ErrorCode.PARAM_INVALID, "Query parameters are invalid");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    private static final class DatasetQueryAccessException extends TensorException {
        private DatasetQueryAccessException() {
            super(ErrorCode.DATASET_MISCONFIGURED, "Dataset metadata is unavailable");
        }
    }
}
```

Do not inspect exception messages, catch `DataAccessException`/`RuntimeException`, check plugin download readiness, or add a Controller advice.

- [ ] **Step 7: Run the fixed MySQL GREEN and correct only implementation defects**

Run:

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DatasetControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `DatasetControllerIT` 8/8, zero failures/errors/skips. If compilation or assertions fail, change only the four task files and preserve every frozen route, type, message, order, MySQL requirement, and serializer boundary. Save successful output as `/private/tmp/m09-t04-green.log`.

- [ ] **Step 8: Run the existing and new integration flows together**

Run:

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am \
  -Dtest=DatasetControllerIT,DownloadControllerIT,FixtureFlowIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `DatasetControllerIT` 8, `DownloadControllerIT` 10, `FixtureFlowIT` 5, total 23/23, zero failures/errors/skips; every class completes against MySQL 8.4.6 without a skipped container.

- [ ] **Step 9: Prove catalog-first and serialization behavior with controlled mutations**

Perform each mutation separately, run the focused IT, capture the expected failure, and restore the production file before continuing:

```text
Mutation A: remove the catalog absence/unsupported-filter checks or move them after datasetQueryService.query.
Expected: rejectsMissingAndUnsafeDatasetMetadataBeforeDatabaseAccess fails on status, error code, or non-zero connection count.

Mutation B1: remove the Long.class serializer.
Expected: exposesExactSurfacesAndImmutableDtos or normalizesAnOutOfRangePageAndSerializesPreciseRows fails because BIGINT is a JSON number.

Mutation B2: replace BigDecimal.toPlainString() with BigDecimal.toString().
Expected: exposesExactSurfacesAndImmutableDtos fails because 1E+3 remains scientific notation.

Mutation B3: additionally register the string serializer for long.class.
Expected: exposesExactSurfacesAndImmutableDtos or normalizesAnOutOfRangePageAndSerializesPreciseRows fails because pagination totals become JSON strings.
```

After restoring, rerun Step 7 and expect 8/8. Store logs only under `/private/tmp/m09-t04-mutation-{catalog,long,decimal,primitive}.log`; never stage them.

- [ ] **Step 10: Run reactor and static/package gates**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

Expected: both commands report plugin-api 79, core 75, Tushare 93, fixture 12, app 36, total 295/295, zero failures/errors/skips. All six Enforcer executions, app ArchUnit, and forbidden-Git tests pass. `DatasetControllerIT` compiles but its `*IT` name does not change default Surefire discovery.

Run:

```bash
rg -n '@RestController|@GetMapping|DatasetCatalog|DatasetQueryService|QueryCriteria|PARAM_INVALID|DATASET_MISCONFIGURED' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java
rg -n 'SimpleModule|Long\.class|BigDecimal\.class|toPlainString' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java
rg -n '@(Post|Put|Patch|Delete)Mapping|JdbcTemplate|SELECT |INSERT |UPDATE |DELETE |setObject|getObject|doubleValue|floatValue|(?i:token|credential)' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/web/(DatasetController|JacksonPrecisionConfiguration|dto/PageResponse)\.class'
git diff --check
git status --short --untracked-files=all -- data-plane
```

Expected: authorized scans show the exact controller/precision mechanisms; the forbidden scan has no output and exits 1; JAR output contains all three top-level production types; format passes; scoped status contains only the four task files and no unrelated artifact.

- [ ] **Step 11: Verify protected paths, clean, and stage exactly the implementation**

Run:

```bash
git diff --quiet -- \
  docs data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/main/resources \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DataSourceResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DatasetDefinitionResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/db \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DataSourceControllerTest.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RequestIdFilterTest.java \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-fixture data-plane/tensor-plugin-tushare
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short --untracked-files=all -- data-plane
```

Expected: protected paths and format pass; clean succeeds; status lists exactly the four new Java files. Stage only:

```bash
git add \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java
git diff --cached --check
git diff --cached --name-only
```

Expected: cached names are exactly those four paths.

- [ ] **Step 12: Commit and re-verify the committed state**

Commit:

```bash
git commit -m "feat(api): expose read-only dataset paging"
```

Run Step 7 focused IT, Step 8 combined IT, and the Step 10 reactor `verify` command again on the commit. Then run:

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-status HEAD
git diff --check HEAD^ HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
rg -n '(?i:token|credential|password|authorization)|m09-t04-secret-sentinel' \
  /private/tmp/m09-t04-red.log \
  /private/tmp/m09-t04-green.log \
  /private/tmp/m09-t04-mutation-catalog.log \
  /private/tmp/m09-t04-mutation-long.log \
  /private/tmp/m09-t04-mutation-decimal.log \
  /private/tmp/m09-t04-mutation-primitive.log
```

Expected: the commit message and four-file scope are exact; fresh focused/combined/reactor results retain 8/23/295 expected counts; clean succeeds; working tree is empty; the final sensitive-text scan has no output and exits 1. Delete the six `/private/tmp/m09-t04-*.log` files after recording only non-sensitive command results in the task completion evidence.
