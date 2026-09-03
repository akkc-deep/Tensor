package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.GenericQueryRepository;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import com.akkc.tensor.web.dto.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ConnectionBuilder;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.ShardingKeyBuilder;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class DatasetControllerIT {
    private static final String TABLE = "fixture__query_records";
    private static final String PATH = "/api/v1/data-sources/fixture/datasets/query_records/records";
    private static final String REQUEST_ID = "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";
    private static final PluginId PLUGIN_ID = PluginId.of("fixture");
    private static final ApiName API_NAME = ApiName.of("query_records");
    private static final DatasetKey DATASET_KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
    private static final Instant INGESTED_AT = Instant.parse("2026-08-07T08:09:10.123Z");
    private static final List<String> COLUMNS = List.of(
            "ts_code",
            "trade_date",
            "ann_date",
            "amount",
            "volume",
            "note",
            "source_plugin",
            "source_api",
            "ingested_at");
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
                MYSQL.getJdbcUrl()
                        + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                MYSQL.getUsername(),
                MYSQL.getPassword());
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

    @Test
    void exposesExactSurfacesAndImmutableDtos() throws Exception {
        assertThat(Modifier.isFinal(DatasetController.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(JacksonPrecisionConfiguration.class.getModifiers())).isTrue();
        assertThat(DatasetController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(DatasetCatalog.class, DatasetQueryService.class));
        assertThat(Arrays.stream(DatasetController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())))
                .singleElement()
                .satisfies(method -> assertThat(method.getName()).isEqualTo("listDatasetRecords"));
        ConditionalOnWebApplication condition =
                DatasetController.class.getAnnotation(ConditionalOnWebApplication.class);
        assertThat(condition).isNotNull();
        assertThat(condition.type()).isEqualTo(ConditionalOnWebApplication.Type.SERVLET);

        assertThat(PageResponse.class.isRecord()).isTrue();
        assertThat(Arrays.stream(PageResponse.class.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getType().getSimpleName()))
                .containsExactly(
                        "requestId:String",
                        "pluginId:String",
                        "apiName:String",
                        "page:int",
                        "pageSize:int",
                        "totalElements:long",
                        "totalPages:long",
                        "columns:List",
                        "items:List");
        assertThat(Arrays.stream(PageResponse.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Modifier.isStatic(method.getModifiers())))
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("from");
                    assertThat(method.getParameterTypes())
                            .containsExactly(String.class, DatasetKey.class, DatasetPage.class);
                    assertThat(method.getReturnType()).isEqualTo(PageResponse.class);
                });

        ArrayList<String> columns = new ArrayList<>(List.of("amount", "volume"));
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("amount", new BigDecimal("1E+3"));
        row.put("volume", Long.MAX_VALUE);
        ArrayList<Map<String, Object>> items = new ArrayList<>();
        items.add(row);
        PageResponse response = new PageResponse(
                REQUEST_ID, "fixture", "query_records", 1, 20, 1, 1, columns, items);
        columns.add("later");
        row.put("amount", BigDecimal.ZERO);
        items.clear();
        assertThat(response.columns()).containsExactly("amount", "volume");
        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("amount", new BigDecimal("1E+3")));
        assertThatThrownBy(() -> response.columns().add("later"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.items().getFirst().put("amount", BigDecimal.TEN))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new PageResponse(
                        null, "fixture", "query_records", 1, 20, 0, 0, List.of("value"), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PageResponse(
                        " ", "fixture", "query_records", 1, 20, 0, 0, List.of("value"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResponse(
                        REQUEST_ID, "", "query_records", 1, 20, 0, 0, List.of("value"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResponse(
                        REQUEST_ID, "fixture", " ", 1, 20, 0, 0, List.of("value"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResponse(
                        REQUEST_ID, "fixture", "query_records", 0, 20, 0, 0, List.of("value"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageResponse.from(null, DATASET_KEY,
                        new DatasetPage(List.of("value"), List.of(), 1, 20, 0, 0)))
                .isInstanceOf(NullPointerException.class);

        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JavaTimeModule(),
                        new JacksonPrecisionConfiguration().precisionModule())
                .build();
        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(response));
        assertThat(json.get("items").get(0).get("amount").textValue()).isEqualTo("1000");
        assertThat(json.get("items").get(0).get("volume").textValue())
                .isEqualTo("9223372036854775807");
        assertThat(List.of("page", "pageSize", "totalElements", "totalPages"))
                .allSatisfy(name -> assertThat(json.get(name).isNumber()).isTrue());
    }

    @Test
    void returnsAnEmptyDefaultPage() throws Exception {
        Flow flow = flow(List.of(definition("ts_code", "trade_date", "ann_date")));

        MvcResult result = flow.mockMvc().perform(get(PATH)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andReturn();

        JsonNode json = flow.objectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.fieldNames()).toIterable().containsExactly(
                "requestId",
                "pluginId",
                "apiName",
                "page",
                "pageSize",
                "totalElements",
                "totalPages",
                "columns",
                "items");
        assertThat(json.get("requestId").textValue()).isEqualTo(REQUEST_ID);
        assertThat(json.get("pluginId").textValue()).isEqualTo("fixture");
        assertThat(json.get("apiName").textValue()).isEqualTo("query_records");
        assertThat(json.get("page").intValue()).isEqualTo(1);
        assertThat(json.get("pageSize").intValue()).isEqualTo(50);
        assertThat(json.get("totalElements").longValue()).isZero();
        assertThat(json.get("totalPages").longValue()).isZero();
        assertThat(json.get("columns")).extracting(JsonNode::textValue).containsExactlyElementsOf(COLUMNS);
        assertThat(json.get("items")).isEmpty();
    }

    @Test
    void supportsUnfilteredTwentyFiftyAndOneHundredRowPages() throws Exception {
        for (int index = 1; index <= 101; index++) {
            insert(
                    "CODE%03d.SZ".formatted(index),
                    LocalDate.of(2026, 8, 7),
                    LocalDate.of(2026, 8, 8),
                    BigDecimal.valueOf(index),
                    index,
                    "row-" + index,
                    INGESTED_AT);
        }
        Flow flow = flow(List.of(definition("ts_code", "trade_date", "ann_date")));

        for (int pageSize : List.of(20, 50, 100)) {
            MvcResult result = flow.mockMvc().perform(get(PATH)
                            .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                            .param("page", "1")
                            .param("pageSize", Integer.toString(pageSize)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode json = flow.objectMapper().readTree(result.getResponse().getContentAsByteArray());
            assertThat(json.get("totalElements").longValue()).isEqualTo(101);
            assertThat(json.get("totalPages").longValue())
                    .isEqualTo(pageSize == 20 ? 6 : pageSize == 50 ? 3 : 2);
            assertThat(json.get("items")).hasSize(pageSize);
            assertThat(json.get("items").get(0).get("ts_code").textValue()).isEqualTo("CODE001.SZ");
            assertThat(json.get("items").get(pageSize - 1).get("ts_code").textValue())
                    .isEqualTo("CODE%03d.SZ".formatted(pageSize));
        }
    }

    @Test
    void appliesAllDeclaredFiltersWithAndSemantics() throws Exception {
        insert("FULL.SZ", LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8),
                BigDecimal.ONE, 1, "match", INGESTED_AT);
        insert("OTHER.SZ", LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 8),
                BigDecimal.TWO, 2, "ts", INGESTED_AT);
        insert("FULL.SZ", LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 8),
                BigDecimal.valueOf(3), 3, "trade", INGESTED_AT);
        insert("FULL.SZ", LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 10),
                BigDecimal.valueOf(4), 4, "ann", INGESTED_AT);
        Flow flow = flow(List.of(definition("ts_code", "trade_date", "ann_date")));

        MvcResult result = flow.mockMvc().perform(get(PATH)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .param("tsCode", "FULL.SZ")
                        .param("tradeDateFrom", "2026-08-07")
                        .param("tradeDateTo", "2026-08-07")
                        .param("annDateFrom", "2026-08-08")
                        .param("annDateTo", "2026-08-08"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = flow.objectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.get("items")).hasSize(1);
        assertThat(json.get("items").get(0).get("note").textValue()).isEqualTo("match");
        assertThat(json.get("columns")).extracting(JsonNode::textValue).containsExactlyElementsOf(COLUMNS);
        assertThat(json.get("items").get(0).fieldNames()).toIterable().containsExactlyElementsOf(COLUMNS);

        MvcResult boundaryResult = flow.mockMvc().perform(get(PATH)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .param("tradeDateFrom", "2026-08-09"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode boundaryJson = flow.objectMapper()
                .readTree(boundaryResult.getResponse().getContentAsByteArray());
        assertThat(boundaryJson.get("items")).hasSize(1);
        assertThat(boundaryJson.get("items").get(0).get("note").textValue()).isEqualTo("trade");
    }

    @Test
    void rejectsUnsupportedAndInvalidParametersBeforeDatabaseAccess() throws Exception {
        Flow restricted = flow(List.of(definition("ts_code")));
        assertControlledError(restricted, get(PATH).param("tradeDateFrom", "2026-08-07"),
                400, ErrorCode.PARAM_INVALID, "Query parameters are invalid");
        assertControlledError(restricted, get(PATH).param("annDateTo", "2026-08-07"),
                400, ErrorCode.PARAM_INVALID, "Query parameters are invalid");

        Flow full = flow(List.of(definition("ts_code", "trade_date", "ann_date")));
        List<MockHttpServletRequestBuilder> invalid = List.of(
                get(PATH).param("tsCode", "bad value"),
                get(PATH).param("tradeDateFrom", "2026-08-08").param("tradeDateTo", "2026-08-07"),
                get(PATH).param("annDateFrom", "2026-08-08").param("annDateTo", "2026-08-07"),
                get(PATH).param("page", "0"),
                get(PATH).param("pageSize", "10"));
        for (MockHttpServletRequestBuilder request : invalid) {
            assertControlledError(full, request, 400, ErrorCode.PARAM_INVALID,
                    "Query parameters are invalid");
        }
        for (MockHttpServletRequestBuilder request : List.of(
                get(PATH).param("tradeDateFrom", "2026-02-30"),
                get(PATH).param("page", "not-an-integer"))) {
            full.dataSource().reset();
            full.mockMvc().perform(request.header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                    .andExpect(status().isBadRequest());
            assertThat(full.dataSource().connectionCount()).isZero();
        }

        Flow missingMdc = flowWithoutFilter(List.of(definition("ts_code", "trade_date", "ann_date")));
        assertThatThrownBy(() -> missingMdc.mockMvc().perform(get(PATH)))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Request ID is unavailable");
        assertThat(missingMdc.dataSource().connectionCount()).isZero();
    }

    @Test
    void rejectsMissingAndUnsafeDatasetMetadataBeforeDatabaseAccess() throws Exception {
        Flow missing = flow(List.of());
        assertControlledError(missing, get(PATH), 409, ErrorCode.DATASET_MISCONFIGURED,
                "Dataset metadata is unavailable");
        assertControlledError(missing, get(PATH).param("page", "0"),
                409, ErrorCode.DATASET_MISCONFIGURED, "Dataset metadata is unavailable");

        Flow unsafe = flow(List.of(definition("note")));
        assertControlledError(unsafe, get(PATH), 409, ErrorCode.DATASET_MISCONFIGURED,
                "Dataset metadata is unavailable");
    }

    @Test
    void normalizesAnOutOfRangePageAndSerializesPreciseRows() throws Exception {
        for (int index = 1; index <= 23; index++) {
            boolean precise = index == 23;
            insert(
                    "CODE%03d.SZ".formatted(index),
                    LocalDate.of(2026, 8, 7),
                    LocalDate.of(2026, 8, 8),
                    precise
                            ? new BigDecimal("12345678901234567890.123456789012345678")
                            : BigDecimal.valueOf(index),
                    precise ? Long.MAX_VALUE : index,
                    precise ? null : "row-" + index,
                    INGESTED_AT);
        }
        Flow flow = flow(List.of(definition("ts_code", "trade_date", "ann_date")));

        MvcResult result = flow.mockMvc().perform(get(PATH)
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .param("page", "99")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = flow.objectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.get("page").intValue()).isEqualTo(2);
        assertThat(json.get("pageSize").intValue()).isEqualTo(20);
        assertThat(json.get("totalElements").longValue()).isEqualTo(23);
        assertThat(json.get("totalPages").longValue()).isEqualTo(2);
        assertThat(json.get("items")).hasSize(3);
        assertThat(json.get("items")).extracting(item -> item.get("ts_code").textValue())
                .containsExactly("CODE021.SZ", "CODE022.SZ", "CODE023.SZ");
        JsonNode precise = json.get("items").get(2);
        assertThat(precise.fieldNames()).toIterable().containsExactlyElementsOf(COLUMNS);
        assertThat(precise.get("amount").textValue())
                .isEqualTo("12345678901234567890.123456789012345678");
        assertThat(precise.get("volume").textValue()).isEqualTo("9223372036854775807");
        assertThat(precise.get("trade_date").textValue()).isEqualTo("2026-08-07");
        assertThat(precise.get("ann_date").textValue()).isEqualTo("2026-08-08");
        assertThat(precise.get("ingested_at").textValue()).isEqualTo("2026-08-07T08:09:10.123Z");
        assertThat(precise.get("note").isNull()).isTrue();
        assertThat(List.of("page", "pageSize", "totalElements", "totalPages"))
                .allSatisfy(name -> assertThat(json.get(name).isNumber()).isTrue());
    }

    @Test
    void doesNotExposeMutatingDatasetRoutes() throws Exception {
        Flow flow = flow(List.of(definition("ts_code", "trade_date", "ann_date")));

        for (MockHttpServletRequestBuilder request : List.of(
                post(PATH), put(PATH), patch(PATH), delete(PATH))) {
            flow.dataSource().reset();
            flow.mockMvc().perform(request.header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                    .andExpect(status().isMethodNotAllowed());
            assertThat(flow.dataSource().connectionCount()).isZero();
        }
    }

    private static void assertControlledError(
            Flow flow,
            MockHttpServletRequestBuilder request,
            int status,
            ErrorCode code,
            String message) throws Exception {
        flow.dataSource().reset();
        MvcResult result = flow.mockMvc().perform(
                        request.header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(status))
                .andReturn();
        assertThat(result.getResolvedException())
                .isInstanceOf(TensorException.class)
                .satisfies(exception -> {
                    TensorException tensorException = (TensorException) exception;
                    assertThat(tensorException.code()).isEqualTo(code);
                    assertThat(tensorException).hasMessage(message);
                });
        assertThat(flow.dataSource().connectionCount()).isZero();
    }

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

    private static Flow flow(List<DatasetDefinition> definitions) {
        return flow(definitions, true);
    }

    private static Flow flowWithoutFilter(List<DatasetDefinition> definitions) {
        return flow(definitions, false);
    }

    private static Flow flow(List<DatasetDefinition> definitions, boolean installRequestIdFilter) {
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
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        StandaloneMockMvcBuilder builder = MockMvcBuilders
                .standaloneSetup(new DatasetController(catalog, queryService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper));
        if (installRequestIdFilter) {
            builder.addFilters(new RequestIdFilter());
        }
        MockMvc mockMvc = builder.build();
        dataSource.reset();
        return new Flow(mockMvc, objectMapper, dataSource);
    }

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

    private record Flow(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            CountingDataSource dataSource) {}

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
}
