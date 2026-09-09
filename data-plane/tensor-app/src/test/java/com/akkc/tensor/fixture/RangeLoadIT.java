package com.akkc.tensor.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.core.download.DownloadExecutionResult;
import com.akkc.tensor.core.download.DownloadExecutionSlot;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.fixture.support.ControlledRangeSource;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.fixture.FixtureBatchSource;
import com.akkc.tensor.plugin.fixture.FixtureConfiguration;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** Fixed-size actual database writes, with sampled measurements rather than performance gates. */
class RangeLoadIT {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final Path OUT = Path.of("../../.superpowers/sdd/RANGE-T18-design/load").toAbsolutePath();
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");
    static HikariDataSource pool;
    static JdbcTemplate raw;
    static final DateTimeFormatter COMPACT = DateTimeFormatter.BASIC_ISO_DATE;

    @BeforeAll static void start() throws Exception {
        MYSQL.start();
        pool = new HikariDataSource();
        pool.setJdbcUrl(MYSQL.getJdbcUrl()); pool.setUsername(MYSQL.getUsername()); pool.setPassword(MYSQL.getPassword());
        pool.setMaximumPoolSize(4);
        raw = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        var migration = Flyway.configure().dataSource(pool).locations("classpath:db/migration").load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(8);
        assertThat(migration.validateWithResult().validationSuccessful).isTrue();
        Files.createDirectories(OUT);
        JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve("environment.json").toFile(), Map.of(
                "jdk", System.getProperty("java.runtime.version"), "os", System.getProperty("os.name"),
                "cpu", command("sysctl", "-n", "machdep.cpu.brand_string"), "physicalMemoryBytes", command("sysctl", "-n", "hw.memsize"),
                "jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments(), "maxHeapBytes", Runtime.getRuntime().maxMemory(),
                "mysql", raw.queryForObject("SELECT VERSION()", String.class), "batchSize", 500,
                "samplePeriodMillis", 50, "measurement", "Test JVM includes the controlled source; RSS and heap are sampled, not absolute instantaneous peaks"));
    }
    @AfterAll static void stop() { if (pool != null) pool.close(); MYSQL.stop(); }

    @Test void thirtyOneDaysWithThreePagesEach() throws Exception { fixtureLoad("multi-day", 31, 600, 200, false); }
    @Test void tenThousandIndependentStockTransactions() throws Exception { fixtureLoad("high-stock", 1, 10000, 500, true); }

    private void fixtureLoad(String name, int days, int stocks, int pageSize, boolean independent) throws Exception {
        long generationStart = System.nanoTime();
        JsonNode script = fixtureScript(days, stocks, pageSize);
        long generationNanos = System.nanoTime() - generationStart;
        try (var source = new ControlledRangeSource(script); var context = fixtureContext(source, independent)) {
            DataSourcePlugin plugin = context.getBean(DataSourcePlugin.class);
            DatasetDefinition definition = context.getBean(DatasetAdapter.class).definition();
            DownloadService service = service(plugin, definition);
            String table = definition.tableName().value();
            // Explicit small warm-up uses the same runtime path and a separately generated small source.
            source.replaceScript(fixtureScript(1, 2, 2));
            clear(table);
            verifyResults(List.of(execute(service, definition, fixtureParams(1))), independent ? 2 : 1, 2, false);
            clear(table);
            source.replaceScript(script);
            List<Map<String,Object>> measurements = new ArrayList<>();
            for (int round = 1; round <= 3; round++) {
                clear(table); source.clearCalls();
                String stem = name + "-" + round;
                measurements.add(measure(stem + "-insert", generationNanos,
                        () -> List.of(execute(service, definition, fixtureParams(days))), independent ? stocks : days,
                        (long) days * stocks, false, source, days, days * ((stocks + pageSize - 1) / pageSize), table));
                source.clearCalls();
                measurements.add(measure(stem + "-update", generationNanos,
                        () -> List.of(execute(service, definition, fixtureParams(days))), independent ? stocks : days,
                        (long) days * stocks, true, source, days, days * ((stocks + pageSize - 1) / pageSize), table));
            }
            JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve(name + "-summary.json").toFile(), measurements);
        }
    }

    @Test void fiveThousandRowsUseAllEightyFiveIncomeColumns() throws Exception {
        DatasetDefinition definition = new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(),
                "classpath:datasets/tushare_pro/income.yaml").getFirst();
        assertThat(definition.columns()).hasSize(85);
        long generationStart = System.nanoTime();
        JsonNode script = incomeScript(definition, 100, 50, 10);
        long generationNanos = System.nanoTime() - generationStart;
        String table = definition.tableName().value();
        try (var source = new ControlledRangeSource(script)) {
            DataSourcePlugin plugin = incomePlugin(definition, source);
            DownloadService service = service(plugin, definition);
            source.replaceScript(incomeScript(definition, 1, 2, 2));
            clear(table);
            verifyResults(incomeExecution(service, definition, 1), 1, 2, false);
            clear(table); source.replaceScript(script);
            List<Map<String,Object>> measurements = new ArrayList<>();
            for (int round = 1; round <= 3; round++) {
                clear(table); source.clearCalls();
                measurements.add(measure("income-" + round + "-insert", generationNanos,
                        () -> incomeExecution(service, definition, 100), 100, 5000, false, source, 100, 500, table));
                for (var column : definition.columns()) {
                    assertThat(raw.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE `" + column.name() + "` IS NULL", Long.class))
                            .as("non-null real income column %s", column.name()).isZero();
                }
                source.clearCalls();
                measurements.add(measure("income-" + round + "-update", generationNanos,
                        () -> incomeExecution(service, definition, 100), 100, 5000, true, source, 100, 500, table));
            }
            JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve("income-summary.json").toFile(), measurements);
        }
    }

    private static Map<String,Object> measure(String name, long generationNanos, Supplier<List<DownloadExecutionResult>> execution,
            long units, long rows, boolean update, ControlledRangeSource source, int batches, int pages, String table) throws Exception {
        List<DownloadExecutionResult> results = null;
        long elapsed = 0;
        var beforeSchema = schema(table);
        JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve(name + "-schema-before.json").toFile(), beforeSchema);
        Sampler sampler = new Sampler();
        Throwable executionFailure = null;
        try (sampler) {
            long start = System.nanoTime();
            try { results = execution.get(); }
            finally { elapsed = System.nanoTime() - start; }
        } catch (Exception | Error failure) {
            executionFailure = failure;
            throw failure;
        } finally {
            // Persist the attempted round even if execution, sampling, or later assertions fail.
            try {
                sampler.write(OUT.resolve(name + "-memory.csv"));
                var attempt = new LinkedHashMap<String,Object>();
                attempt.put("elapsedNanos", elapsed); attempt.put("scriptGenerationNanos", generationNanos);
                attempt.put("executionError", executionFailure == null ? null : executionFailure.getClass().getName());
                attempt.put("returnedResults", results); attempt.put("sourceCalls", source.calls());
                JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve(name + "-attempt.json").toFile(), attempt);
                JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve(name + "-sql.json").toFile(), Map.of(
                        "business", raw.queryForList("SELECT * FROM " + table + " ORDER BY "
                                + (table.endsWith("income") ? "ts_code, ann_date, end_date, report_type" : "ts_code, trade_date")),
                        "tasks", raw.queryForList("SELECT * FROM tensor_download_task ORDER BY task_id"),
                        "items", raw.queryForList("SELECT * FROM tensor_download_task_item ORDER BY task_id,target_type,target_value,time_type,time_value")));
                JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve(name + "-schema-after.json").toFile(), schema(table));
            } catch (Exception | Error evidenceFailure) {
                if (executionFailure != null) executionFailure.addSuppressed(evidenceFailure);
                else throw evidenceFailure;
            }
        }
        assertThat(schema(table)).isEqualTo(beforeSchema);
        verifyResults(results, units, rows, update);
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isEqualTo(rows);
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Long.class)).isZero();
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM tensor_download_task_item", Long.class)).isZero();
        var calls = source.calls();
        assertThat(calls).hasSize(pages);
        assertThat(calls.stream().map(c -> c.path("params")).distinct().count()).isEqualTo(batches);
        assertThat(calls).allSatisfy(c -> assertThat(c.path("path").asText()).isEqualTo("/batch"));
        for (JsonNode params : calls.stream().map(c -> c.path("params")).distinct().toList()) {
            List<Integer> sequence = calls.stream().filter(c -> c.path("params").equals(params)).map(c -> c.path("page").asInt()).toList();
            assertThat(sequence).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, sequence.size()).boxed().toList());
        }
        sampler.write(OUT.resolve(name + "-memory.csv"));
        var summary = new LinkedHashMap<String,Object>();
        summary.put("name", name); summary.put("elapsedNanos", elapsed); summary.put("scriptGenerationNanos", generationNanos);
        summary.put("samples", sampler.samples.size()); summary.put("heapStartBytes", sampler.samples.getFirst()[1]);
        summary.put("heapEndBytes", sampler.samples.getLast()[1]); summary.put("heapPeakBytes", sampler.samples.stream().mapToLong(s -> s[1]).max().orElseThrow());
        summary.put("rssStartBytes", sampler.samples.getFirst()[2]); summary.put("rssEndBytes", sampler.samples.getLast()[2]);
        summary.put("rssPeakBytes", sampler.samples.stream().mapToLong(s -> s[2]).max().orElseThrow());
        summary.put("batches", batches); summary.put("pages", pages); summary.put("completedUnits", units); summary.put("failedUnits", 0);
        summary.put("notStartedUnits", 0); summary.put("skippedClosedDates", 0); summary.put("sourceRowCount", rows);
        summary.put("insertedRows", update ? 0 : rows); summary.put("updatedRows", update ? rows : 0);
        JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve(name + "-summary.json").toFile(), summary);
        System.out.println("RANGE_LOAD " + JSON.writeValueAsString(summary));
        return summary;
    }

    private static List<Map<String,Object>> schema(String businessTable) {
        var definition = new ArrayList<Map<String,Object>>();
        for (String table : List.of(businessTable, "tensor_download_task", "tensor_download_task_item")) {
            definition.addAll(raw.queryForList("SHOW CREATE TABLE " + table));
            definition.addAll(raw.queryForList("SHOW INDEX FROM " + table).stream().map(row -> {
                row.remove("Cardinality"); // Row-dependent optimizer estimates are not index definitions.
                return row;
            }).toList());
        }
        return definition;
    }

    private static void verifyResults(List<DownloadExecutionResult> results, long units, long rows, boolean update) {
        assertThat(results).allSatisfy(r -> {
            assertThat(r.outcome()).isEqualTo(DownloadExecutionResult.Outcome.SUCCESS);
            assertThat(r.failedUnits()).isZero(); assertThat(r.notStartedUnits()).isZero(); assertThat(r.skippedClosedDates()).isZero();
            assertThat(r.taskId()).isNull(); assertThat(r.remainingFailedUnits()).isZero();
        });
        assertThat(results.stream().mapToLong(DownloadExecutionResult::completedUnits).sum()).isEqualTo(units);
        assertThat(results.stream().mapToLong(DownloadExecutionResult::sourceRowCount).sum()).isEqualTo(rows);
        assertThat(results.stream().mapToLong(DownloadExecutionResult::insertedRows).sum()).isEqualTo(update ? 0 : rows);
        assertThat(results.stream().mapToLong(DownloadExecutionResult::updatedRows).sum()).isEqualTo(update ? rows : 0);
    }
    private static void clear(String table) {
        raw.update("DELETE FROM tensor_download_task_item"); raw.update("DELETE FROM tensor_download_task"); raw.update("DELETE FROM " + table);
    }
    private static DownloadExecutionResult execute(DownloadService service, DatasetDefinition definition, Map<String,Object> params) {
        return service.executeInitial(definition.datasetKey().pluginId(), definition.datasetKey().apiName(), params, new RequestId(UUID.randomUUID()));
    }
    private static List<DownloadExecutionResult> incomeExecution(DownloadService service, DatasetDefinition definition, int stocks) {
        List<DownloadExecutionResult> results = new ArrayList<>();
        for (int i = 1; i <= stocks; i++) results.add(execute(service, definition, Map.of(
                "ts_code", stock(i), "start_date", "20260901", "end_date", "20260901")));
        return results;
    }
    private static Map<String,Object> fixtureParams(int days) { return Map.of("scenario", "SUCCESS", "start_date", "20260801", "end_date", "202608%02d".formatted(days)); }
    private static String stock(int i) { return "%06d.SZ".formatted(i); }

    private static AnnotationConfigApplicationContext fixtureContext(ControlledRangeSource source, boolean independent) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("acceptance");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("range-load", Map.of(
                "tensor.plugins.fixture.enabled", "true", "tensor.plugins.fixture.mode", "ANN_DATE_RANGE",
                "tensor.plugins.fixture.recovery", independent ? "STOCK_TIME" : "REQUEST", "tensor.plugins.fixture.source-url", source.url().toString())));
        context.register(FixtureConfiguration.class); context.refresh(); return context;
    }
    private static DownloadService service(DataSourcePlugin plugin, DatasetDefinition definition) {
        var config = new ApplicationConfiguration();
        var adapters = config.tensorDatasetAdapters(List.of(definition), new DefaultListableBeanFactory().getBeanProvider(DatasetAdapter.class));
        var catalog = config.datasetCatalog(adapters, pool);
        var registry = config.adapterRegistry(adapters, catalog);
        var jdbc = new JdbcTemplate(pool); var transactions = new DataSourceTransactionManager(pool);
        var persistence = config.persistenceService(catalog, config.datasetLockManager(), config.existingKeyRepository(jdbc),
                config.genericUpsertRepository(jdbc), transactions);
        var repository = config.retryTaskRepository(jdbc, config.taskParametersJson());
        var clock = Clock.systemUTC();
        var storage = config.retryTaskStorageService(repository, transactions, clock);
        var commits = config.batchCommitService(persistence, repository, config.parameterValidator(), transactions, clock);
        return new DownloadService(new PluginRegistry(List.of(plugin)), registry, config.parameterValidator(), persistence,
                commits, storage, new DownloadExecutionSlot(), clock);
    }
    private static DataSourcePlugin incomePlugin(DatasetDefinition definition, ControlledRangeSource source) {
        var refs = List.of("RangeLoadIT controlled source; not real Tushare evidence");
        var completeness = new DownloadPolicy.CompletenessPolicy(DownloadPolicy.CompletenessStatus.UNCONFIRMED,
                DownloadPolicy.CompletenessStatus.UNCONFIRMED, "Controlled paginated protocol", "Controlled paginated protocol");
        var policy = new DownloadPolicy(DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.DateSemantic.ANN_DATE, "Controlled income load",
                null, new DownloadPolicy.Limits(31), DownloadPolicy.SourceRequestMode.DATE, "ann_date",
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE, DownloadPolicy.BatchPlanning.SINGLE_DATE,
                new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false, refs), completeness, null, refs);
        var api = new ApiDescriptor(definition.datasetKey().apiName(), definition.displayName(), definition.category(), definition.queryMode(),
                DownloadParameterProjection.project(definition.parameters(), policy), policy, definition.parameters());
        var client = new FixtureBatchSource(source.url(), definition);
        return new DataSourcePlugin() {
            public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }
            public PluginDescriptor descriptor() { return new PluginDescriptor(definition.datasetKey().pluginId(), "Controlled", "Controlled",
                    true, true, true, null, List.of(api), List.of(definition.datasetKey())); }
            public DownloadPolicy.BatchPlanning planBatch(ApiName name, FetchBatch batch, DownloadContext context) { return DownloadPolicy.BatchPlanning.SINGLE_DATE; }
            public FetchResult fetchBatch(ApiName name, FetchBatch batch, DownloadContext context) { return client.fetch(batch, context); }
            public FetchResult download(ApiName name, Map<String,Object> params, DownloadContext context) { throw new AssertionError("No legacy fallback"); }
        };
    }

    private static JsonNode fixtureScript(int days, int stocks, int pageSize) {
        ObjectNode script = JSON.createObjectNode(); ArrayNode rules = script.putArray("batchRules"); script.putArray("calendarRules");
        for (int d = 1; d <= days; d++) {
            String date = "202608%02d".formatted(d);
            for (int offset = 0, page = 1; offset < stocks; offset += pageSize, page++) {
                ArrayNode data = JSON.createArrayNode();
                for (int stock = offset + 1; stock <= Math.min(stocks, offset + pageSize); stock++)
                    data.add(JSON.valueToTree(List.of(stock(stock), date, "11.230000000000000001", "controlled-load")));
                addPage(rules, "fixture_daily", Map.of("scenario", "SUCCESS", "ann_date", date), page,
                        List.of("ts_code", "trade_date", "amount", "note"), data, stocks, offset + pageSize >= stocks);
            }
        }
        return script;
    }
    private static JsonNode incomeScript(DatasetDefinition definition, int stocks, int periods, int pageSize) {
        ObjectNode script = JSON.createObjectNode(); ArrayNode rules = script.putArray("batchRules"); script.putArray("calendarRules");
        for (int s = 1; s <= stocks; s++) for (int offset = 0, page = 1; offset < periods; offset += pageSize, page++) {
            ArrayNode data = JSON.createArrayNode();
            for (int period = offset; period < Math.min(periods, offset + pageSize); period++) {
                ArrayNode row = JSON.createArrayNode();
                for (var column : definition.columns()) {
                    String value = switch (column.name()) {
                        case "ts_code" -> stock(s);
                        case "ann_date", "f_ann_date" -> "20260901";
                        case "end_date" -> YearMonth.of(2013, 3).plusMonths(period * 3L).atEndOfMonth().format(COMPACT);
                        default -> column.logicalType() == com.akkc.tensor.plugin.api.dataset.LogicalType.DECIMAL ? "1.230000000000000001" : "1";
                    };
                    row.add(value);
                }
                assertThat(row).hasSize(85); data.add(row);
            }
            addPage(rules, "income", Map.of("ts_code", stock(s), "ann_date", "20260901"), page,
                    definition.columns().stream().map(c -> c.name()).toList(), data, periods, offset + pageSize >= periods);
        }
        return script;
    }
    private static void addPage(ArrayNode rules, String api, Map<String,Object> params, int page, List<String> fields, ArrayNode data, int total, boolean last) {
        ObjectNode rule = rules.addObject(); rule.put("apiName", api); rule.set("params", JSON.valueToTree(params)); rule.put("page", page);
        ObjectNode response = rule.putObject("response"); response.set("fields", JSON.valueToTree(fields)); response.set("data", data);
        response.put("totalRows", total); response.put("complete", last);
        if (last) response.putNull("nextPage"); else response.put("nextPage", page + 1);
    }

    private static String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).start();
        if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() != 0) throw new AssertionError("Cannot sample host with " + args[0]);
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }
    private static final class Sampler implements AutoCloseable {
        final List<long[]> samples = new CopyOnWriteArrayList<>();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final ScheduledFuture<?> future;
        final long start = System.nanoTime();
        Sampler() throws Exception { sample(); future = executor.scheduleAtFixedRate(() -> {
            try { sample(); } catch (Exception e) { throw new IllegalStateException("Memory sample failed", e); }
        }, 50, 50, TimeUnit.MILLISECONDS); }
        void sample() throws Exception {
            long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long rss = Long.parseLong(command("ps", "-o", "rss=", "-p", Long.toString(ProcessHandle.current().pid()))) * 1024;
            samples.add(new long[]{System.nanoTime() - start, heap, rss});
        }
        public void close() throws Exception {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) throw new AssertionError("Memory sampler did not stop");
            if (future.isDone() && !future.isCancelled()) future.get();
            sample();
        }
        void write(Path path) throws Exception {
            StringBuilder csv = new StringBuilder("elapsed_nanos,heap_used_bytes,rss_bytes\n");
            samples.forEach(s -> csv.append(s[0]).append(',').append(s[1]).append(',').append(s[2]).append('\n'));
            Files.writeString(path, csv);
        }
    }
}
