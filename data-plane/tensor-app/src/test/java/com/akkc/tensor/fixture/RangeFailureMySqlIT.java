package com.akkc.tensor.fixture;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.core.download.*;
import com.akkc.tensor.core.retry.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.fixture.support.ControlledRangeSource;
import com.akkc.tensor.plugin.api.*;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.fixture.FixtureBatchSource;
import com.akkc.tensor.plugin.tushare.metadata.*;
import com.akkc.tensor.web.*;
import com.akkc.tensor.web.download.*;
import com.akkc.tensor.web.dto.*;
import com.akkc.tensor.observability.*;
import com.fasterxml.jackson.databind.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Controlled real definitions, real migrations/transactions, independent raw SQL; no real Tushare calls. */
@Testcontainers
class RangeFailureMySqlIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final Path EVIDENCE = Path.of("../../.superpowers/sdd/RANGE-T18-design/mysql");
    static final Clock CLOCK = Clock.systemUTC();
    static DataSource raw;
    static JdbcTemplate sql;
    static Map<ApiName,DownloadPolicy> policies;
    final List<Object> results = new ArrayList<>();
    final List<String> triggers = new ArrayList<>();
    final Map<String,Object> schemas = new TreeMap<>();
    ControlledRangeSource source;
    DatasetDefinition definition;
    ApiDescriptor api;
    Probe probe;
    DownloadService initial;
    RetryDownloadService retries;
    RetryTaskStorageService storage;
    RetryTaskQueryService queries;
    DownloadController controller;
    RetryTaskController retryController;
    DownloadParameterResolver resolver;

    @BeforeAll static void migrate() throws Exception {
        raw = new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        sql = new JdbcTemplate(raw);
        Flyway.configure().dataSource(raw).locations("classpath:db/migration").load().migrate();
        policies = new DownloadPolicyLoader().load(new ClassPathResource("download/tushare-pro-policies.yaml"));
        Files.createDirectories(EVIDENCE);
    }
    @BeforeEach void prepare() throws Exception {
        sql.update("DELETE FROM tensor_download_task_item"); sql.update("DELETE FROM tensor_download_task");
        source = new ControlledRangeSource(JSON.createObjectNode());
        configure(load("daily"), com.akkc.tensor.test.DownloadPolicies.tradeRange());
    }
    @AfterEach void close() throws Exception {
        for (String trigger:triggers) sql.execute("DROP TRIGGER IF EXISTS "+trigger);
        for (var entry:schemas.entrySet()) assertThat(schema(entry.getKey())).as("schema unchanged: %s",entry.getKey()).isEqualTo(entry.getValue());
        source.close();
    }
    static DatasetDefinition load(String name) {
        return new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(),"classpath:datasets/tushare_pro/"+name+".yaml").getFirst();
    }
    void configure(DatasetDefinition selected,DownloadPolicy policy) {
        definition=selected;
        api=new ApiDescriptor(selected.datasetKey().apiName(),"Controlled","Controlled real definition",selected.queryMode(),
                (policy.sourceRequestMode()==DownloadPolicy.SourceRequestMode.RANGE?selected.parameters():DownloadParameterProjection.project(selected.parameters(),policy)),policy,selected.parameters());
        assertThat(api.sourceParameters()).isEqualTo(selected.parameters());
        for(String table:List.of(selected.tableName().value(),"tensor_download_task","tensor_download_task_item")) schemas.putIfAbsent(table,schema(table));
        sql.update("DELETE FROM `"+selected.tableName().value()+"`");
        probe=new Probe(raw);
        wire(false);
    }
    void wire(boolean confirmedThenThrow) {
        var client=new FixtureBatchSource(source.url(),definition);
        var key=definition.datasetKey();
        DataSourcePlugin plugin=new DataSourcePlugin() {
            public PluginDescriptor descriptor() { return new PluginDescriptor(key.pluginId(),"Controlled","Controlled",true,true,true,null,List.of(api),List.of(key)); }
            public PluginReadiness readiness() { return new PluginReadiness(true,true,true,null); }
            public FetchResult download(ApiName n,Map<String,Object> p,DownloadContext c) { throw new AssertionError("legacy fallback"); }
            public DownloadPolicy.BatchPlanning planBatch(ApiName n,FetchBatch b,DownloadContext c) { c.checkServerState(); return api.downloadPolicy().batchPlanning(); }
            public FetchResult fetchBatch(ApiName n,FetchBatch b,DownloadContext c) { return client.fetch(b,c); }
            public CalendarDecision confirmCalendar(ApiName n,CalendarScope s,DownloadContext c) { return client.confirmCalendar(s,c); }
        };
        var config=new ApplicationConfiguration();
        var adapters=config.tensorDatasetAdapters(List.of(definition),new DefaultListableBeanFactory().getBeanProvider(DatasetAdapter.class));
        var catalog=config.datasetCatalog(adapters,probe);
        var registry=config.adapterRegistry(adapters,catalog);
        var jdbc=new JdbcTemplate(probe);
        var delegate=new DataSourceTransactionManager(probe);
        PlatformTransactionManager tx=confirmedThenThrow ? new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition d) { return delegate.getTransaction(d); }
            public void rollback(TransactionStatus s) { delegate.rollback(s); }
            public void commit(TransactionStatus s) { delegate.commit(s); if(s.isNewTransaction()&&probe.armed) { probe.armed=false; throw new TransactionSystemException("synthetic confirmed completion"); } }
        }:delegate;
        var persistence=config.persistenceService(catalog,config.datasetLockManager(),config.existingKeyRepository(jdbc),config.genericUpsertRepository(jdbc),tx);
        var repository=config.retryTaskRepository(jdbc,config.taskParametersJson());
        storage=config.retryTaskStorageService(repository,tx,CLOCK);
        var commits=config.batchCommitService(persistence,repository,config.parameterValidator(),tx,CLOCK);
        var plugins=new PluginRegistry(List.of(plugin));
        var slot=new DownloadExecutionSlot();
        initial=new DownloadService(plugins,registry,config.parameterValidator(),persistence,commits,storage,slot,CLOCK);
        retries=new RetryDownloadService(plugins,registry,config.parameterValidator(),commits,storage,slot,CLOCK);
        queries=config.retryTaskQueryService(storage,plugins,registry,config.parameterValidator(),slot);
        resolver=new DownloadParameterResolver(new DownloadDescriptorResolver(plugins,registry),config.parameterValidator());
        var logger=new OperationLogger(plugins,new TensorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),plugins));
        controller=new DownloadController(initial,logger,resolver);
        retryController=new RetryTaskController(queries,retries,logger);
        probe.statements.clear();
    }
    static DownloadPolicy policy(DownloadPolicy.Mode mode,DownloadPolicy.SourceRequestMode request,boolean stock) {
        var base=com.akkc.tensor.test.DownloadPolicies.original();
        var semantic=switch(mode) { case TRADE_DATE_RANGE->DownloadPolicy.DateSemantic.TRADE_DATE; case ANN_DATE_RANGE->DownloadPolicy.DateSemantic.ANN_DATE; case MONTH_RANGE->DownloadPolicy.DateSemantic.COVERED_MONTH; case NATIVE_RANGE->DownloadPolicy.DateSemantic.CALENDAR_DATE; default->DownloadPolicy.DateSemantic.NONE; };
        String date=request==DownloadPolicy.SourceRequestMode.DATE?(mode==DownloadPolicy.Mode.TRADE_DATE_RANGE?"trade_date":"ann_date"):request==DownloadPolicy.SourceRequestMode.MONTH?"month":null;
        var planning=switch(request) { case DATE->DownloadPolicy.BatchPlanning.SINGLE_DATE; case MONTH->DownloadPolicy.BatchPlanning.SINGLE_MONTH; case RANGE->DownloadPolicy.BatchPlanning.SOURCE_RANGE; default->DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS; };
        return new DownloadPolicy(mode,semantic,"Controlled source only",mode==DownloadPolicy.Mode.TRADE_DATE_RANGE?DownloadPolicy.CalendarProfile.C_A:null,
                mode==DownloadPolicy.Mode.ORIGINAL_PARAMS?null:new DownloadPolicy.Limits(31),request,date,DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,planning,
                stock?new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME,"ts_code",mode==DownloadPolicy.Mode.ANN_DATE_RANGE?"ann_date":"trade_date",request==DownloadPolicy.SourceRequestMode.RANGE?RecoverySelector.TimeType.RANGE:RecoverySelector.TimeType.DATE,true,List.of("docs/test.md")):base.recoveryPolicy(),
                base.completenessPolicy(),mode==DownloadPolicy.Mode.TRADE_DATE_RANGE?DownloadPolicy.CalendarEvidenceStatus.DOCUMENTED:null,base.evidenceRefs());
    }
    static DatasetDefinition changed(DatasetDefinition d,List<ParameterDescriptor> params) {
        return new DatasetDefinition(d.datasetKey(),d.displayName(),d.category(),d.queryMode(),params,d.tableName(),d.columns(),d.businessKey(),d.filters(),d.fixedColumn(),2);
    }
    static DatasetDefinition audit() {
        var d=load("fina_audit");
        return changed(d,d.parameters().stream().map(p->p.name().equals("ts_code")?new ParameterDescriptor(p.name(),p.label(),p.description(),p.type(),false,p.defaultValue(),p.allowedValues(),p.pattern(),p.relatedParameter()):p).toList());
    }
    static DatasetDefinition dailyRange() {
        var d=load("daily");
        var p=new ArrayList<>(load("namechange").parameters());
        p.addAll(audit().parameters().stream().filter(x->x.name().equals("ts_code")).toList());
        return changed(d,p);
    }
    static Map<String,Object> range(String first,String last) { return Map.of("start_date",first.replace("-",""),"end_date",last.replace("-","")); }
    static RecoverySelector selector(String stock,String type,String value) { return new RecoverySelector(stock.isEmpty()?RecoverySelector.TargetType.REQUEST:RecoverySelector.TargetType.STOCK,stock,RecoverySelector.TimeType.valueOf(type),value); }
    static RecoverySelector day(int d) { return selector("","DATE",LocalDate.of(2026,9,d).toString()); }
    UUID task(Map<String,Object> params,RecoverySelector... items) {
        UUID id=storage.create(definition.datasetKey(),params,new RetryTaskRepository.Failure(items[0],ErrorCode.SOURCE_RATE_LIMITED)).key().taskId();
        for(int i=1;i<items.length;i++) storage.append(id,new RetryTaskRepository.Failure(items[i],ErrorCode.SOURCE_RATE_LIMITED));
        return id;
    }
    DownloadExecutionResult initial(Map<String,Object> params) { return record(()->initial.executeInitial(definition.datasetKey().pluginId(),definition.datasetKey().apiName(),params,new RequestId(UUID.randomUUID()))); }
    DownloadExecutionResult retry(UUID id) { return record(()->retries.execute(id,new RequestId(UUID.randomUUID()))); }
    DownloadExecutionResult record(Supplier<DownloadExecutionResult> action) { var r=action.get(); results.add(DownloadResponse.from(r)); return r; }
    DownloadExecutionResult stopped(Supplier<DownloadExecutionResult> action,ErrorCode code) {
        var e=catchThrowableOfType(action::get,DownloadExecutionException.class);
        assertThat(e).isNotNull().hasNoCause(); assertThat(e.code()).isEqualTo(code);
        results.add(Map.of("errorCode",e.code(),"response",DownloadResponse.from(e.downloadResult())));
        return e.downloadResult();
    }
    Map<String,Object> rule(Map<String,Object> params,Object response) { return Map.of("apiName",definition.datasetKey().apiName().value(),"params",params,"page",1,"response",response); }
    Map<String,Object> page(List<List<Object>> rows) {
        var p=new LinkedHashMap<String,Object>(); p.put("fields",definition.columns().stream().map(c->c.name()).toList()); p.put("data",rows);p.put("totalRows",rows.size());p.put("nextPage",null);p.put("complete",true);return p;
    }
    static Map<String,Object> error(String code) { return Map.of("errorCode",code); }
    void script(List<Map<String,Object>> batches,Map<String,Object> params,List<String> dates,Set<String> closed) {
        var root=new LinkedHashMap<String,Object>();root.put("batchRules",batches);
        if(!dates.isEmpty()) { var days=new LinkedHashMap<String,Boolean>();dates.forEach(d->days.put(d,!closed.contains(d)));
            root.put("calendarRules",List.of(Map.of("apiName",definition.datasetKey().apiName().value(),"params",params,"dates",dates,"response",Map.of("calendars",Map.of("SSE",days,"SZSE",days))))); }
        source.replaceScript(JSON.valueToTree(root));source.clearCalls();
    }
    void script(List<Map<String,Object>> batches) { script(batches,Map.of(),List.of(),Set.of()); }
    static List<String> dates(int first,int last) { return java.util.stream.IntStream.rangeClosed(first,last).mapToObj(i->LocalDate.of(2026,9,i).toString()).toList(); }
    List<JsonNode> batches() { return source.calls().stream().filter(c->c.has("page")).toList(); }
    static List<Object> daily(String stock,String date,String amount) { return Arrays.asList(stock,date,null,null,null,null,null,null,null,null,amount); }
    static List<Object> auditRow(String stock,String period,String amount) { return Arrays.asList(stock,"20260902",period,"Approved",amount,null,null); }
    void trigger(String table,String timing,String condition) {
        String name="t18_sql_"+triggers.size();triggers.add(name);
        sql.execute("CREATE TRIGGER "+name+" BEFORE "+timing+" ON "+table+" FOR EACH ROW BEGIN IF "+condition+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic SQL failure'; END IF; END");
    }
    void removeTriggers() { for(String name:triggers) sql.execute("DROP TRIGGER IF EXISTS "+name); }
    static List<Map<String,Object>> headers() { return sql.queryForList("SELECT * FROM tensor_download_task ORDER BY task_id"); }
    static List<Map<String,Object>> items() { return sql.queryForList("SELECT * FROM tensor_download_task_item ORDER BY task_id,target_type,target_value,time_type,time_value"); }
    List<Map<String,Object>> business() { return sql.queryForList("SELECT * FROM `"+definition.tableName().value()+"` ORDER BY "+String.join(",",definition.businessKey().fields())); }
    static Object schema(String table) {
        var indexes=sql.queryForList("SHOW INDEX FROM `"+table+"`"); indexes.forEach(row->row.remove("Cardinality"));
        return Map.of("create",sql.queryForList("SHOW CREATE TABLE `"+table+"`"),"indexes",indexes);
    }
    void evidence(String name) throws Exception {
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("layer","service plus production DownloadResponse mapper; native DATE case uses actual controllers");
        snapshot.put("sourceCalls",source.calls());snapshot.put("responses",results);snapshot.put("business",business());snapshot.put("tasks",headers());snapshot.put("items",items());snapshot.put("executedSql",probe.statements);
        snapshot.put("schemaBefore",schemas);var after=new TreeMap<String,Object>();schemas.keySet().forEach(t->after.put(t,schema(t)));snapshot.put("schemaAfter",after);
        JSON.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve(name+".json").toFile(),snapshot);
    }

    @Test void firstMultiDaySegmentFailsAndSecondCommitsInSameInitialExecution() throws Exception {
        configure(dailyRange(),policy(DownloadPolicy.Mode.TRADE_DATE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,false));
        var original=range("20260901","20260907");
        script(List.of(rule(range("20260901","20260903"),error("SOURCE_TIMEOUT")),
                rule(range("20260905","20260907"),page(List.of(daily("000001.SZ","20260905","5"),daily("000001.SZ","20260906","6"),daily("000001.SZ","20260907","7"))))),original,dates(1,7),Set.of("2026-09-04"));
        var r=initial(original);
        assertThat(r.completedUnits()).isEqualTo(1);assertThat(r.failedUnits()).isEqualTo(1);assertThat(r.skippedClosedDates()).isEqualTo(1);
        assertThat(r.insertedRows()).isEqualTo(3);assertThat(r.notStartedUnits()).isZero();
        assertThat(batches().stream().map(c->c.path("params"))).containsExactly(JSON.valueToTree(range("20260901","20260903")),JSON.valueToTree(range("20260905","20260907")));
        assertThat(items()).singleElement().satisfies(i->assertThat(i).containsEntry("target_type","REQUEST").containsEntry("time_type","RANGE").containsEntry("time_value","2026-09-01/2026-09-03"));
        assertThat(new TaskParametersJson().read((String)headers().getFirst().get("task_params"))).isEqualTo(original);
        assertThat(business()).hasSize(3);evidence("two-multi-day-segments");
    }

    @ParameterizedTest @ValueSource(strings={"REQUEST_VALIDATION","REQUEST_SQL","STOCK_RANGE"})
    void exactRangeRetryNeverCropsOrUpgradesSavedObject(String variant) throws Exception {
        boolean stock=variant.equals("STOCK_RANGE");
        configure(dailyRange(),policy(DownloadPolicy.Mode.NATIVE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,stock));
        var selected=selector(stock?"000001.SZ":"","RANGE","2026-09-01/2026-09-03");
        var original=range("20260901","20260910"); UUID id=task(original,selected);
        var params=new HashMap<String,Object>(range("20260901","20260903"));if(stock) params.put("ts_code","000001.SZ");
        script(List.of(rule(params,page(List.of(daily("000001.SZ","20260901",variant.equals("REQUEST_VALIDATION")?"bad":"9"),daily(stock?"000001.SZ":"000002.SZ","20260902","2"))))));
        if(!variant.equals("REQUEST_VALIDATION")) trigger(definition.tableName().value(),"INSERT",stock?"NEW.ts_code='000001.SZ'":"NEW.ts_code='000002.SZ'");
        var r=retry(id);assertThat(r.completedUnits()).isZero();assertThat(r.failedUnits()).isEqualTo(1);assertThat(r.remainingFailedUnits()).isEqualTo(1);
        assertThat(items()).singleElement().satisfies(i->assertThat(i).containsEntry("target_type",selected.targetType().name()).containsEntry("time_value",selected.timeValue()));
        assertThat(batches()).hasSize(1);assertThat(batches().getFirst().path("params")).isEqualTo(JSON.valueToTree(params));assertThat(business()).isEmpty();
        assertThat(items().getFirst().get("error_code")).isEqualTo(variant.equals("REQUEST_VALIDATION")?"ADAPTER_TYPE_INVALID":"PERSISTENCE_FAILED");
        evidence("exact-range-"+variant);
        removeTriggers(); script(List.of(rule(params,page(List.of(daily("000001.SZ","20260901","9"),daily("000001.SZ","20260903","3"))))));
        var recovered=retry(id);assertThat(recovered.completedUnits()).isEqualTo(1);assertThat(recovered.insertedRows()).isEqualTo(2);assertThat(headers()).isEmpty();assertThat(items()).isEmpty();evidence("exact-range-recovered-"+variant);
    }

    @Test void independentStocksKeepAAndCWhenBSecondSqlGroupRollsBackAndRetryDeletesPrecisely() throws Exception {
        configure(audit(),policy(DownloadPolicy.Mode.ANN_DATE_RANGE,DownloadPolicy.SourceRequestMode.DATE,true));
        sql.update("INSERT INTO tushare_pro__fina_audit(ts_code,ann_date,end_date,audit_fees,source_plugin,source_api,ingested_at) VALUES ('000002.SZ','2026-09-02','2026-03-31',8,'tushare_pro','fina_audit','2026-09-01 00:00:00')");
        var old=sql.queryForMap("SELECT * FROM tushare_pro__fina_audit");
        var bRows=List.of(auditRow("000002.SZ","20260331","9"),auditRow("000002.SZ","20260630","2"),auditRow("000002.SZ","20260930","4"));
        var all=new ArrayList<List<Object>>();all.add(auditRow("000001.SZ","20260630","1"));all.addAll(bRows);all.add(auditRow("600000.SH","20260630","3"));
        script(List.of(rule(Map.of("ann_date","20260902"),page(all))));
        trigger(definition.tableName().value(),"INSERT","NEW.ts_code='000002.SZ' AND NEW.end_date='2026-09-30'");
        var first=initial(range("20260902","20260902"));
        assertThat(first.completedUnits()).isEqualTo(2);assertThat(first.failedUnits()).isEqualTo(1);assertThat(first.insertedRows()).isEqualTo(2);assertThat(first.updatedRows()).isZero();
        assertThat(business()).hasSize(3);assertThat(sql.queryForList("SELECT * FROM tushare_pro__fina_audit WHERE ts_code='000002.SZ'")).containsExactly(old);
        assertThat(items()).singleElement().satisfies(i->assertThat(i).containsEntry("target_value","000002.SZ").containsEntry("error_code","PERSISTENCE_FAILED"));
        assertThat(probe.statements.stream().filter(q->q.startsWith("INSERT INTO `tushare_pro__fina_audit`")).count()).isEqualTo(4);evidence("stock-second-sql-group");
        UUID id=first.taskId();removeTriggers();
        script(List.of(rule(Map.of("ann_date","20260902","ts_code","000002.SZ"),page(bRows))));
        trigger("tensor_download_task_item","DELETE","OLD.task_id='"+id+"' AND OLD.target_value='000002.SZ' AND OLD.time_type='DATE' AND OLD.time_value='2026-09-02'");
        var failed=retry(id);assertThat(failed.failedUnits()).isEqualTo(1);assertThat(failed.sourceRowCount()).isZero();assertThat(failed.insertedRows()).isZero();assertThat(failed.updatedRows()).isZero();
        assertThat(sql.queryForList("SELECT * FROM tushare_pro__fina_audit WHERE ts_code='000002.SZ'")).containsExactly(old);assertThat(business()).hasSize(3);assertThat(items()).hasSize(1);evidence("precise-item-delete-rollback");
        removeTriggers();source.clearCalls();var done=retry(id);assertThat(done.insertedRows()).isEqualTo(2);assertThat(done.updatedRows()).isEqualTo(1);assertThat(business()).hasSize(5);assertThat(items()).isEmpty();assertThat(headers()).isEmpty();evidence("precise-item-delete-recovered");
    }

    @Test void finalHeaderDeleteFailureRollsBackInsertAndUpdateTogether() throws Exception {
        configure(dailyRange(),policy(DownloadPolicy.Mode.NATIVE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,false));
        UUID id=task(range("20260901","20260903"),selector("","RANGE","2026-09-01/2026-09-03"));
        sql.update("INSERT INTO tushare_pro__daily(ts_code,trade_date,amount,source_plugin,source_api,ingested_at) VALUES('000001.SZ','2026-09-01',8,'tushare_pro','daily','2026-09-01 00:00:00')");
        var old=business(); var frozen=sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task");
        script(List.of(rule(range("20260901","20260903"),page(List.of(daily("000001.SZ","20260901","9"),daily("000001.SZ","20260902","2"))))));
        trigger("tensor_download_task","DELETE","OLD.task_id='"+id+"'");
        var fail=retry(id);assertThat(fail.failedUnits()).isEqualTo(1);assertThat(fail.sourceRowCount()).isZero();assertThat(business()).isEqualTo(old);assertThat(items()).hasSize(1);
        assertThat(sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task")).isEqualTo(frozen);evidence("final-header-delete-rollback");
        removeTriggers();source.clearCalls();var ok=retry(id);assertThat(ok.insertedRows()).isEqualTo(1);assertThat(ok.updatedRows()).isEqualTo(1);assertThat(business()).hasSize(2);assertThat(headers()).isEmpty();assertThat(items()).isEmpty();evidence("final-header-delete-recovered");
    }

    @ParameterizedTest @ValueSource(strings={"trade_cal","new_share","namechange"})
    void nativeDateInitialAndRetryUseIdenticalCompactNativeKeysThroughRealControllers(String name) throws Exception {
        var production=policies.get(new ApiName(name));
        var controlled=policy(DownloadPolicy.Mode.NATIVE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,false);
        configure(load(name),new DownloadPolicy(controlled.mode(),production.dateSemantic(),controlled.description(),null,controlled.limits(),controlled.sourceRequestMode(),null,controlled.requestEvidenceStatus(),controlled.batchPlanning(),controlled.recoveryPolicy(),controlled.completenessPolicy(),null,controlled.evidenceRefs()));
        var params=new HashMap<String,Object>(range("20260903","20260903"));if(name.equals("trade_cal"))params.put("exchange","SSE");
        script(List.of(rule(params,error("SOURCE_TIMEOUT"))));
        org.slf4j.MDC.put(RequestIdFilter.MDC_KEY,UUID.randomUUID().toString());
        try {
            var first=controller.download(new DownloadRequest(definition.datasetKey(),resolver.resolveDownload(definition.datasetKey(),params),params.keySet()));results.add(first);
            var node=JSON.valueToTree(first);String id=node.path("taskId").asText();
            assertThat(items()).singleElement().satisfies(i->assertThat(i).containsEntry("time_type","DATE").containsEntry("time_value","2026-09-03"));
            assertThat(batches()).hasSize(1);assertThat(batches().getFirst().path("params")).isEqualTo(JSON.valueToTree(params));evidence("native-date-first-"+name);
            List<Object> row=switch(name) {
                case "trade_cal"->Arrays.asList("SSE","20260903",1,"20260902");
                case "new_share"->Arrays.asList("000001.SZ",null,null,"20260903",null,null,null,null,null,null,null,null);
                default->Arrays.asList("000001.SZ","Controlled Name","20260903",null,"20260903",null);
            };
            script(List.of(rule(params,page(List.of(row)))));
            var response=retryController.execute(id,new org.springframework.mock.web.MockHttpServletRequest());results.add(response);
            assertThat(JSON.valueToTree(response).path("insertedRows").asLong()).isEqualTo(1);
            assertThat(batches()).hasSize(1);assertThat(batches().getFirst().path("params")).isEqualTo(JSON.valueToTree(params));assertThat(business()).hasSize(1);assertThat(headers()).isEmpty();assertThat(items()).isEmpty();evidence("native-date-retry-"+name);
        } finally { org.slf4j.MDC.remove(RequestIdFilter.MDC_KEY); }
    }

    @Test void monthRetryUsesFullSeptemberAndOldMissingDatesAreNotInferred() throws Exception {
        configure(load("broker_recommend"),policy(DownloadPolicy.Mode.MONTH_RANGE,DownloadPolicy.SourceRequestMode.MONTH,false));
        var original=range("20260815","20260903");
        script(List.of(rule(Map.of("month","202608"),page(List.of(Arrays.asList("202608","Controlled","000001.SZ",null)))),rule(Map.of("month","202609"),error("SOURCE_TIMEOUT"))));
        var first=initial(original);UUID id=first.taskId();assertThat(first.completedUnits()).isEqualTo(1);assertThat(first.failedUnits()).isEqualTo(1);
        var saved=sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task");
        assertThat(new TaskParametersJson().read((String)saved.get("task_params"))).isEqualTo(original);
        assertThat(items()).singleElement().satisfies(i->assertThat(i).containsEntry("time_type","MONTH").containsEntry("time_value","2026-09"));evidence("month-partial-original");
        script(List.of(rule(Map.of("month","202609"),error("SOURCE_RATE_LIMITED"))));retry(id);
        assertThat(batches()).hasSize(1);assertThat(batches().getFirst().path("params")).isEqualTo(JSON.valueToTree(Map.of("month","202609")));
        assertThat(sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task")).isEqualTo(saved);evidence("month-full-september-fails");
        script(List.of(rule(Map.of("month","202609"),page(List.of(Arrays.asList("202609","Controlled","000001.SZ",null))))));retry(id);
        assertThat(business()).hasSize(2);assertThat(headers()).isEmpty();assertThat(items()).isEmpty();evidence("month-full-september-success");
        UUID old=task(Map.of(),selector("","MONTH","2026-09"));
        assertThat(queries.get(old).summary().originalDateRangeStatus()).isEqualTo(RetryTaskQueryService.OriginalRangeStatus.NOT_RECORDED);
        results.add(queries.get(old));evidence("old-missing-dates-before");
        source.clearCalls();var repeated=retry(old);assertThat(repeated.updatedRows()).isEqualTo(1);assertThat(batches().getFirst().path("params")).isEqualTo(JSON.valueToTree(Map.of("month","202609")));assertThat(headers()).isEmpty();evidence("old-missing-dates-recovered");
    }

    @ParameterizedTest @ValueSource(strings={"hs_const","index_classify","index_member","index_member_all","pledge_detail","pledge_stat","stk_holdernumber","stk_managers","stk_rewards","stock_basic","stock_company"})
    void elevenOriginalContractsKeepExactConditionsWithoutDateEndpoints(String name) throws Exception {
        var d=load(name);assertThat(policies.get(d.datasetKey().apiName()).mode()).isEqualTo(DownloadPolicy.Mode.ORIGINAL_PARAMS);
        configure(d,com.akkc.tensor.test.DownloadPolicies.original());
        assertThat(api.parameters()).isEqualTo(d.parameters());assertThat(api.parameters()).noneMatch(p->Set.of("start_date","end_date").contains(p.name()));
        var params=new LinkedHashMap<String,Object>();
        for(var p:d.parameters()) if(p.required()) params.put(p.name(),p.type()==ParameterType.TS_CODE?"000001.SZ":p.allowedValues().getFirst());
        script(List.of(rule(params,error("SOURCE_TIMEOUT"))));var r=initial(params);
        assertThat(items()).singleElement().satisfies(i->assertThat(i).containsEntry("target_type","REQUEST").containsEntry("time_type","NONE").containsEntry("time_value",""));
        assertThat(new TaskParametersJson().read((String)headers().getFirst().get("task_params"))).isEqualTo(params);
        assertThat(batches().getFirst().path("params")).isEqualTo(JSON.valueToTree(params));evidence("original-conditions-first-"+name);
        script(List.of(rule(params,page(List.of()))));var empty=retry(r.taskId());assertThat(empty.completedUnits()).isEqualTo(1);assertThat(empty.sourceRowCount()).isZero();
        assertThat(batches().getFirst().path("params")).isEqualTo(JSON.valueToTree(params));assertThat(business()).isEmpty();assertThat(headers()).isEmpty();assertThat(items()).isEmpty();evidence("original-conditions-retry-"+name);
    }

    @ParameterizedTest @ValueSource(strings={"structure","selector","unknown-type","code","conflict","non-executable","json-text"})
    void invalidStoredTasksRefuseBeforeSourceAndPreserveActualSql(String variant) throws Exception {
        configure(dailyRange(),policy(DownloadPolicy.Mode.NATIVE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,true));
        var params=new HashMap<String,Object>(range("20260901","20260903"));if(variant.equals("conflict"))params.put("ts_code","000001.SZ");
        UUID id=task(params,selector(variant.equals("conflict")?"000002.SZ":"","DATE","2026-09-02"));
        switch(variant) {
            case "structure"->sql.update("UPDATE tensor_download_task SET task_params=JSON_OBJECT('start_date',JSON_ARRAY('bad'),'end_date','20260903') WHERE task_id=?",id.toString());
            case "selector"->sql.update("UPDATE tensor_download_task_item SET time_value='2026-02-30' WHERE task_id=?",id.toString());
            case "unknown-type"->probe.corruptSelector=true; // SQL CHECK forbids unknown enum; inject only the repository read boundary.
            case "code"->sql.update("UPDATE tensor_download_task_item SET error_code='INTERNAL_ERROR' WHERE task_id=?",id.toString());
            case "non-executable"->{ var p=api.downloadPolicy();api=new ApiDescriptor(api.apiName(),api.displayName(),"Controlled",api.queryMode(),api.parameters(),new DownloadPolicy(p.mode(),p.dateSemantic(),p.description(),p.calendarProfile(),p.limits(),null,null,DownloadPolicy.RequestEvidenceStatus.UNCONFIRMED,DownloadPolicy.BatchPlanning.UNCONFIRMED,p.recoveryPolicy(),p.completenessPolicy(),p.calendarEvidenceStatus(),p.evidenceRefs()),api.sourceParameters());wire(false); }
            case "json-text"->probe.corruptJson=true; // Actual repository ResultSet boundary; MySQL JSON itself remains valid.
            default->{}
        }
        var beforeHeaders=headers();var beforeItems=items();
        var failure=catchThrowableOfType(()->retry(id),TensorException.class);assertThat(failure).isNotNull();results.add(Map.of("errorCode",failure.code(),"layer",Set.of("json-text","unknown-type").contains(variant)?"actual repository JDBC ResultSet boundary injection; bypass SQL remains constrained valid data":"actual stored data"));
        assertThat(source.calls()).isEmpty();assertThat(headers()).isEqualTo(beforeHeaders);assertThat(items()).isEqualTo(beforeItems);assertThat(business()).isEmpty();evidence("invalid-saved-"+variant);
    }

    @Test void committedStockIsNotRewrappedWhenNextDateRequestFails() throws Exception {
        configure(audit(),policy(DownloadPolicy.Mode.ANN_DATE_RANGE,DownloadPolicy.SourceRequestMode.DATE,true));
        script(List.of(rule(Map.of("ann_date","20260902"),page(List.of(auditRow("000001.SZ","20260630","1")))),rule(Map.of("ann_date","20260903"),error("SOURCE_TIMEOUT"))));
        var r=initial(range("20260902","20260903"));assertThat(r.completedUnits()).isEqualTo(1);assertThat(r.failedUnits()).isEqualTo(1);assertThat(business()).hasSize(1);
        assertThat(items()).singleElement().satisfies(i->assertThat(i).containsEntry("target_type","REQUEST").containsEntry("time_value","2026-09-03"));evidence("stock-success-then-request-failure");
    }

    @ParameterizedTest @ValueSource(strings={"first-insert","append","reason-update"})
    void failureSaveRejectionStopsLaterSourceAndKeepsOnlyConfirmedFacts(String stage) throws Exception {
        configure(dailyRange(),policy(DownloadPolicy.Mode.ANN_DATE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,false));
        var original=range("20260901","20260903");
        if(stage.equals("reason-update")) {
            UUID id=task(original,day(1),day(2),day(3));var before=items();
            script(List.of(rule(range("20260901","20260901"),error("SOURCE_TIMEOUT"))));
            trigger("tensor_download_task_item","UPDATE","OLD.task_id='"+id+"' AND OLD.time_value='2026-09-01'");
            var r=stopped(()->retry(id),ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);assertThat(r.failedUnits()).isEqualTo(1);assertThat(r.notStartedUnits()).isEqualTo(2);assertThat(r.remainingFailedUnits()).isNull();assertThat(items()).isEqualTo(before);assertThat(batches()).hasSize(1);
        } else {
            // DATE strategy provides three independent plans, with a prior confirmed business success.
            configure(load("daily"),com.akkc.tensor.test.DownloadPolicies.tradeRange());
            var rules=List.of(rule(Map.of("trade_date","20260901"),page(List.of(daily("000001.SZ","20260901","1")))),rule(Map.of("trade_date","20260902"),error("SOURCE_TIMEOUT")),rule(Map.of("trade_date","20260903"),error("SOURCE_RATE_LIMITED")),rule(Map.of("trade_date","20260904"),page(List.of(daily("000001.SZ","20260904","4")))));
            var four=range("20260901","20260904");script(rules,four,dates(1,4),Set.of());
            trigger("tensor_download_task_item","INSERT",stage.equals("first-insert")?"NEW.time_value='2026-09-02'":"NEW.time_value='2026-09-03'");
            var r=stopped(()->initial(four),ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);assertThat(r.completedUnits()).isEqualTo(1);assertThat(r.insertedRows()).isEqualTo(1);assertThat(r.remainingFailedUnits()).isNull();assertThat(r.notStartedUnits()).isEqualTo(stage.equals("first-insert")?2:1);
            assertThat(batches()).hasSize(stage.equals("first-insert")?2:3);assertThat(business()).hasSize(1);assertThat(items()).hasSize(stage.equals("first-insert")?0:1);assertThat(headers()).hasSize(stage.equals("first-insert")?0:1);
            if(stage.equals("first-insert"))assertThat(r.taskId()).isNull();else assertThat(r.taskId().toString()).isEqualTo(headers().getFirst().get("task_id"));
        }
        evidence("save-rejection-"+stage);
    }

    @ParameterizedTest @CsvSource({"business,false","business,true","create,false","create,true","reason,false","reason,true","cleanup,false","cleanup,true"})
    void commitUnknownDoesNotReconcileOrContinueWhetherDelegateCommittedOrRolledBack(String phase,boolean physicallyCommitted) throws Exception {
        configure(dailyRange(),policy(DownloadPolicy.Mode.NATIVE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,false));
        var original=range("20260901","20260903");UUID id=null;
        if(phase.equals("reason")||phase.equals("cleanup"))id=task(original,day(1),day(2));
        Map<String,Object> params=(id==null)?original:range("20260901","20260901");
        script(List.of(rule(params,phase.equals("reason")||phase.equals("create")?error("SOURCE_TIMEOUT"):page(List.of(daily("000001.SZ","20260901","1"))))));
        probe.phase=phase;probe.afterCommit=physicallyCommitted;probe.armed=true;
        UUID selected=id;
        var r=stopped(()->selected==null?initial(original):retry(selected),phase.equals("reason")||phase.equals("create")?ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED:ErrorCode.COMMIT_UNCONFIRMED);
        assertThat(probe.armed).isFalse();assertThat(batches()).hasSize(1);assertThat(r.completedUnits()).isZero();assertThat(r.insertedRows()).isZero();assertThat(r.updatedRows()).isZero();assertThat(r.sourceRowCount()).isZero();
        if(phase.equals("business")) { assertThat(business()).hasSize(physicallyCommitted?1:0);assertThat(headers()).isEmpty();assertThat(items()).isEmpty();assertThat(r.unconfirmedScopes()).hasSize(1); }
        if(phase.equals("create")) { assertThat(business()).isEmpty();assertThat(headers()).hasSize(physicallyCommitted?1:0);assertThat(items()).hasSize(physicallyCommitted?1:0);assertThat(r.taskId()).isNull();assertThat(r.remainingFailedUnits()).isNull(); }
        if(phase.equals("reason")) { assertThat(business()).isEmpty();assertThat(items()).hasSize(2);assertThat(items().getFirst().get("error_code")).isEqualTo(physicallyCommitted?"SOURCE_TIMEOUT":"SOURCE_RATE_LIMITED");assertThat(r.remainingFailedUnits()).isNull();assertThat(r.notStartedUnits()).isEqualTo(1); }
        if(phase.equals("cleanup")) { assertThat(business()).hasSize(physicallyCommitted?1:0);assertThat(items()).hasSize(physicallyCommitted?1:2);assertThat(r.remainingFailedUnits()).isNull();assertThat(r.notStartedUnits()).isEqualTo(1);assertThat(r.unconfirmedScopes()).containsExactly(day(1)); }
        assertThat(probe.statements.stream().filter(q->q.startsWith("SELECT task_id, plugin_id, api_name, task_params, created_at, updated_at FROM tensor_download_task WHERE task_id=?")&&!q.endsWith("FOR UPDATE")).count()).isEqualTo(id==null?0:1);
        evidence("commit-unknown-"+phase+"-"+(physicallyCommitted?"committed":"rolled-back"));
    }

    @Test void lastCleanupLostCommitReplyDeletesBothTablesButResponseRemainsUnknownAndNextRetryIsNotFound() throws Exception {
        configure(dailyRange(),policy(DownloadPolicy.Mode.NATIVE_RANGE,DownloadPolicy.SourceRequestMode.RANGE,false));
        var params=range("20260901","20260901");UUID id=task(params,day(1));
        script(List.of(rule(params,page(List.of(daily("000001.SZ","20260901","1"))))));
        probe.phase="cleanup";probe.afterCommit=true;probe.armed=true;
        var r=stopped(()->retry(id),ErrorCode.COMMIT_UNCONFIRMED);assertThat(r.completedUnits()).isZero();assertThat(r.sourceRowCount()).isZero();assertThat(r.remainingFailedUnits()).isNull();
        assertThat(business()).hasSize(1);assertThat(headers()).isEmpty();assertThat(items()).isEmpty();evidence("last-cleanup-commit-unknown");
        source.clearCalls();var failure=catchThrowableOfType(()->retry(id),TensorException.class);assertThat(failure.code()).isEqualTo(ErrorCode.RETRY_TASK_NOT_FOUND);assertThat(source.calls()).isEmpty();assertThat(headers()).isEmpty();assertThat(items()).isEmpty();results.add(Map.of("errorCode",failure.code()));evidence("last-cleanup-followup-404");
    }

    @Test void frameworkConfirmedCommitCountsFactBeforeStopping() throws Exception {
        configure(load("daily"),com.akkc.tensor.test.DownloadPolicies.tradeRange());wire(true);
        var params=range("20260901","20260903");script(List.of(rule(Map.of("trade_date","20260901"),page(List.of(daily("000001.SZ","20260901","1"))))),params,dates(1,3),Set.of());
        probe.armed=true; // This exception is after DataSourceTransactionManager completed its COMMITTED callback.
        var r=stopped(()->initial(params),ErrorCode.INTERNAL_ERROR);assertThat(r.completedUnits()).isEqualTo(1);assertThat(r.sourceRowCount()).isEqualTo(1);assertThat(r.insertedRows()).isEqualTo(1);assertThat(r.notStartedUnits()).isEqualTo(2);assertThat(r.unconfirmedScopes()).isEmpty();assertThat(business()).hasSize(1);assertThat(headers()).isEmpty();assertThat(items()).isEmpty();assertThat(batches()).hasSize(1);evidence("framework-confirmed-commit");
    }

    @Test void closedRetryDeletesFailureWithoutTouchingOldBusinessAndCountsClosedDate() throws Exception {
        var params=range("20260901","20260903");UUID id=task(params,day(2));
        sql.update("INSERT INTO tushare_pro__daily(ts_code,trade_date,amount,source_plugin,source_api,ingested_at) VALUES('000001.SZ','2026-09-02',8,'tushare_pro','daily','2026-09-01 00:00:00')");var old=business();
        script(List.of(),params,List.of("2026-09-02"),Set.of("2026-09-02"));
        var r=retry(id);assertThat(r.skippedClosedDates()).isEqualTo(1);assertThat(r.completedUnits()).isZero();assertThat(r.sourceRowCount()).isZero();assertThat(batches()).isEmpty();assertThat(headers()).isEmpty();assertThat(items()).isEmpty();assertThat(business()).isEqualTo(old);evidence("closed-retry-keeps-business");
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void futureStockCardinalityRemainsNullWhenSaveOrDatabaseConnectionStopsExecution(boolean databaseLoss) throws Exception {
        configure(audit(),policy(DownloadPolicy.Mode.ANN_DATE_RANGE,DownloadPolicy.SourceRequestMode.DATE,true));
        var params=range("20260902","20260903");
        script(List.of(rule(Map.of("ann_date","20260902"),databaseLoss?page(List.of(auditRow("000001.SZ","20260630","1"))):error("SOURCE_TIMEOUT"))));
        if(databaseLoss)probe.businessSqlLoss=true;
        else trigger("tensor_download_task_item","INSERT","NEW.time_value='2026-09-02'");
        var r=stopped(()->initial(params),databaseLoss?ErrorCode.PERSISTENCE_FAILED:ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);
        assertThat(r.notStartedUnits()).isNull();assertThat(r.notStartedScopes()).containsExactly(day(3));assertThat(r.completedUnits()).isZero();assertThat(r.sourceRowCount()).isZero();assertThat(r.insertedRows()).isZero();assertThat(r.updatedRows()).isZero();
        if(!databaseLoss)assertThat(r.remainingFailedUnits()).isNull();
        assertThat(batches()).hasSize(1);assertThat(business()).isEmpty();assertThat(headers()).isEmpty();assertThat(items()).isEmpty();evidence("nullable-stock-stop-"+databaseLoss);
    }

    @Test void realSqlCauseCanaryIsRemovedFromControllerStorageAndApplicationLogs() throws Exception {
        configure(load("daily"),com.akkc.tensor.test.DownloadPolicies.tradeRange());
        String canary="T18_CAUSE_CANARY_token_private_response";
        String trigger="t18_cause_canary";triggers.add(trigger);
        sql.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON tushare_pro__daily FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+canary+"'");
        var params=range("20260901","20260901");
        script(List.of(rule(Map.of("trade_date","20260901"),page(List.of(daily("000001.SZ","20260901","1"))))),params,dates(1,1),Set.of());
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();logger.addAppender(logs);
        org.slf4j.MDC.put(RequestIdFilter.MDC_KEY,UUID.randomUUID().toString());
        try {
            var response=controller.download(new DownloadRequest(definition.datasetKey(),resolver.resolveDownload(definition.datasetKey(),params),params.keySet()));
            results.add(response);
            assertThat((Throwable)probe.observedSqlFailure).isNotNull().hasMessageContaining(canary);
            assertThat(probe.observedSqlFailure.getSQLState()).isEqualTo("45000");
            assertThat(JSON.valueToTree(response).path("failedUnits").asLong()).isEqualTo(1);
            assertThat(business()).isEmpty();assertThat(headers()).hasSize(1);assertThat(items()).hasSize(1);
            assertThat(items().getFirst().get("error_code")).isEqualTo("PERSISTENCE_FAILED");
            assertThat(JSON.writeValueAsString(List.of(response,headers(),items()))).doesNotContain(canary);
            var messages=logs.list.stream().map(event->event.getFormattedMessage()+(event.getThrowableProxy()==null?"":ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()))).toList();
            assertThat(messages).anyMatch(message->message.contains("tensor.operation.completed"));
            assertThat(messages).allSatisfy(message->assertThat(message).doesNotContain(canary));
            JSON.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve("sql-cause-logs.json").toFile(),Map.of("realSqlCauseContainedCanary",true,"sqlState","45000","logs",messages));
            evidence("sql-cause-safe-controller-storage");
        } finally { org.slf4j.MDC.remove(RequestIdFilter.MDC_KEY);logger.detachAppender(logs);logs.stop(); }
    }

    /** Local connection probe follows the existing T12/T13 approach; faults are consumed once. */
    static final class Probe extends AbstractDataSource {
        final DataSource delegate;
        final List<String> statements=new ArrayList<>();
        String phase="";
        boolean armed,afterCommit,corruptJson,corruptSelector,businessSqlLoss;
        SQLException observedSqlFailure;
        Probe(DataSource delegate) { this.delegate=delegate; }
        public Connection getConnection(String u,String p) throws SQLException { return getConnection(); }
        public Connection getConnection() throws SQLException {
            Connection c=delegate.getConnection();Set<String> operations=new HashSet<>();
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,args)->{
                if(m.getName().equals("commit")&&armed&&operations.contains(phase)) {
                    armed=false;
                    if(afterCommit)c.commit();else c.rollback();
                    throw new SQLException("synthetic connection loss","08006");
                }
                Object result=invoke(c,m,args);
                if(m.getName().equals("prepareStatement")) {
                    String query=(String)args[0];
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(s,op,values)->{
                        if(Set.of("executeQuery","executeUpdate","executeBatch").contains(op.getName())) {
                            statements.add(query);
                            if(query.startsWith("INSERT INTO `tushare_pro__")) {
                                operations.add("business");
                                if(businessSqlLoss) {businessSqlLoss=false;throw new SQLException("synthetic connection loss","08006");}
                            }
                            if(query.startsWith("INSERT INTO tensor_download_task_item"))operations.add("create");
                            if(query.startsWith("UPDATE tensor_download_task_item SET error_code="))operations.add("reason");
                            if(query.startsWith("DELETE FROM tensor_download_task_item"))operations.add("cleanup");
                        }
                        Object answer;
                        try { answer=invoke(result,op,values); }
                        catch(SQLException failure) { observedSqlFailure=failure;throw failure; }
                        if(corruptSelector&&answer instanceof ResultSet rows&&query.startsWith("SELECT task_id, target_type, target_value, time_type")) {
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(r,read,column)->{
                                if(read.getName().equals("getString")&&column!=null&&Objects.equals(column[0],4))return "UNKNOWN";
                                return invoke(rows,read,column);
                            });
                        }
                        if(corruptJson&&answer instanceof ResultSet rows&&query.startsWith("SELECT task_id, plugin_id, api_name, task_params")) {
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(r,read,column)->{
                                if(read.getName().equals("getString")&&column!=null&&(Objects.equals(column[0],4)||Objects.equals(column[0],"task_params")))return "{broken-json";
                                return invoke(rows,read,column);
                            });
                        }
                        return answer;
                    });
                }
                return result;
            });
        }
    }
    static Object invoke(Object target,Method method,Object[] args) throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
}
