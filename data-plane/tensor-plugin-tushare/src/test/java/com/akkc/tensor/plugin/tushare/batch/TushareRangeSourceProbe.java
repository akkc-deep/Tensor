package com.akkc.tensor.plugin.tushare.batch;

import static com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.PlanningMode.*;
import static com.akkc.tensor.plugin.api.error.ErrorCode.*;
import static com.akkc.tensor.plugin.tushare.batch.TushareBatchPolicies.*;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.core.download.task.DateRangePlanner;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.client.TushareRestClientFactory;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Explicit -Dtest entry only: its name deliberately does not match ordinary Surefire patterns. */
class TushareRangeSourceProbe {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final List<DatasetDefinition> DEFINITIONS = new DatasetDefinitionLoader().loadAll(
            new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");
    private static final Set<String> CASE_KEYS = Set.of("caseId", "apiName", "mode", "params", "dateAxis", "start", "end", "evidenceRefs");
    private static final Pattern UNSAFE = Pattern.compile("jdbc:|\\b(?:token|password|passwd|secret|api[_-]?key|authorization|username|jdbcUrl)[\"']?\\s*[:=]|\\bBearer\\s+\\S+|\\braw[ _-]*(?:response|error|cause|body|payload)\\b|\\bcaused by\\s*:|\\bat\\s+[\\w.$]+\\([^)]*:\\d+\\)|https?://[^/\\s]+@|\\bsk-(?:proj-|svcacct-)?[a-z0-9_-]{16,}|-----BEGIN .*PRIVATE KEY-----|[\\[{]\\s*[\"'{\\[]|(?:^|[^a-z0-9])(?:[a-z0-9]+_)*(?:token|password|passwd|secret|api[_-]?key)[\"']?\\s*[:=]|--(?:token|password|secret|api-key)\\s+\\S+|\\bgh[pousr]_[a-z0-9]{20,}|\\bgithub_pat_[a-z0-9_]{20,}|\\beyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+", Pattern.CASE_INSENSITIVE);

    @FunctionalInterface interface Source {
        DownloadEnvelope execute(DatasetDefinition definition, Map<String,Object> params, BatchCallContext context);
    }

    @Test void explicitlyAuthorizedSourceEvidence() {
        // Do not attach any original exception/cause: Surefire reports are not private artifacts.
        try {
            var env = System.getenv();
            var properties = properties(env);
            JsonNode plan = loadPlan(Path.of(required(env, "ISSUE018_T13_CASES_FILE")));
            Path output = prepareOutput(Path.of(required(env, "ISSUE018_T13_EVIDENCE_DIR")));
            var client = new TushareProClient(new TushareRestClientFactory().create(properties), properties);
            var context = new Context(Clock.systemUTC());
            Thread shutdown = new Thread(context::stop, "source-probe-stop");
            Runtime.getRuntime().addShutdownHook(shutdown);
            JsonNode result;
            try {
                result = run(plan, client::execute, context, evidence -> save(output, evidence));
            } finally {
                context.stop();
                Runtime.getRuntime().removeShutdownHook(shutdown);
            }
            for (JsonNode item : result.path("cases")) {
                if (item.path("status").asText().equals("FAILED")) throw new IllegalStateException();
            }
        } catch (Throwable ignored) {
            throw new AssertionError("Tushare SOURCE probe did not complete; inspect private evidence");
        }
    }

    static TushareProperties properties(Map<String,String> env) {
        try {
            if (!"1".equals(env.get("TENSOR_TUSHARE_LIVE_E2E"))) throw invalid();
            String interval = required(env, "M14_T05_CALL_INTERVAL_MS");
            if (!interval.matches("[0-9]+")) throw invalid();
            long millis = Long.parseLong(interval);
            if (millis < 2000 || millis > 3_600_000) throw invalid();
            return new TushareProperties(true, URI.create("https://api.tushare.pro"),
                    new TushareProperties.Credential(required(env, "TENSOR_TUSHARE_TOKEN")),
                    Duration.ofSeconds(5), Duration.ofSeconds(120), 67_108_864, Duration.ofMillis(millis));
        } catch (Exception ignored) { throw invalid(); }
    }

    static JsonNode loadPlan(Path path) {
        try {
            privatePath(path, false, owner());
            privatePath(path.getParent(), true, owner());
            try (var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                if (channel.size() > 2_097_152) throw invalid();
                var bytes = ByteBuffer.allocate((int)channel.size());
                while (bytes.hasRemaining() && channel.read(bytes) != -1) { }
                return validatePlan(JSON.readTree(Arrays.copyOf(bytes.array(), bytes.position())));
            }
        } catch (Exception ignored) { throw invalid(); }
    }

    static Path prepareOutput(Path directory) {
        try {
            privatePath(directory, true, owner());
            Path output = directory.resolve("source-evidence.json");
            Files.createFile(output, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            privatePath(output, false, owner());
            return output;
        } catch (Exception ignored) { throw invalid(); }
    }

    static void privatePath(Path path, boolean directory, UserPrincipal owner) {
        try {
            if (!path.isAbsolute() || !path.normalize().equals(path) || !path.toRealPath().equals(path)) throw invalid();
            var attributes = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || (directory ? !attributes.isDirectory() : !attributes.isRegularFile())
                    || !attributes.owner().equals(owner)
                    || !attributes.permissions().equals(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))) throw invalid();
        } catch (Exception ignored) { throw invalid(); }
    }

    private static UserPrincipal owner() throws Exception {
        return FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
    }

    private static void save(Path output, JsonNode evidence) {
        try {
            privatePath(output.getParent(), true, owner());
            privatePath(output, false, owner());
            safeStrings(evidence);
            try (SeekableByteChannel channel = Files.newByteChannel(output, Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                ByteBuffer bytes = ByteBuffer.wrap(JSON.writeValueAsBytes(evidence));
                channel.truncate(0);
                while (bytes.hasRemaining()) channel.write(bytes);
            }
        } catch (Exception ignored) { throw invalid(); }
    }

    static JsonNode validatePlan(JsonNode plan) {
        try {
            safeStrings(plan);
            exact(plan, Set.of("runId", "cases")); text(plan.get("runId"));
            if (!plan.get("cases").isArray() || plan.get("cases").isEmpty()) throw invalid();
            var ids = new HashSet<String>();
            for (JsonNode item : plan.get("cases")) {
                exact(item, CASE_KEYS); text(item.get("caseId")); text(item.get("apiName"));
                if (!ids.add(item.get("caseId").textValue())) throw invalid();
                DatasetDefinition definition = definition(item.get("apiName").textValue());
                if (!item.get("evidenceRefs").isArray()) throw invalid();
                for (JsonNode ref : item.get("evidenceRefs")) text(ref);
                String mode = item.get("mode").asText();
                JsonNode params = item.get("params");
                if (mode.equals("SINGLE")) {
                    if (!item.get("dateAxis").isNull() || !item.get("start").isNull() || !item.get("end").isNull()) throw invalid();
                    var expected = new HashSet<String>();
                    for (var parameter : definition.parameters()) expected.add(parameter.name());
                    exact(params, expected);
                    for (var parameter : definition.parameters()) {
                        JsonNode value = params.get(parameter.name()); text(value);
                        switch (parameter.type()) {
                            case TS_CODE -> stock(value);
                            case DATE, DATE_RANGE_MEMBER -> inputDate(value);
                            case ENUM -> { if (!parameter.allowedValues().contains(value.textValue())) throw invalid(); }
                            default -> throw invalid();
                        }
                    }
                    if (params.has("start_date") && inputDate(params.get("start_date")).isAfter(inputDate(params.get("end_date")))) throw invalid();
                    if (definition.datasetKey().apiName().value().equals("stock_company")) {
                        String exchange = Map.of("SH","SSE","SZ","SZSE","BJ","BSE").get(params.get("ts_code").asText().substring(7));
                        if (!exchange.equals(params.get("exchange").asText())) throw invalid();
                    }
                } else if (mode.equals("RANGE")) {
                    Policy policy = productionPolicies().get(definition.datasetKey().apiName());
                    if (policy == null || !policy.dateAxis().name().equals(item.get("dateAxis").asText()) || item.get("evidenceRefs").isEmpty()) throw invalid();
                    String scope = switch (policy.parameterShape()) { case STOCK -> "ts_code"; case EXCHANGE -> "exchange"; case EXCHANGE_ID -> "exchange_id"; case DATES -> null; };
                    exact(params, scope == null ? Set.of("start_date","end_date") : Set.of(scope,"start_date","end_date"));
                    if (scope != null) {
                        if (scope.equals("ts_code")) stock(params.get(scope));
                        else if (!List.of("SSE","SZSE","BSE").contains(params.get(scope).asText())) throw invalid();
                    }
                    LocalDate start = inputDate(item.get("start")), end = inputDate(item.get("end"));
                    if (start.isAfter(end) || !item.get("start").equals(params.get("start_date")) || !item.get("end").equals(params.get("end_date"))) throw invalid();
                } else throw invalid();
            }
            return plan.deepCopy();
        } catch (Exception ignored) { throw invalid(); }
    }

    static JsonNode run(JsonNode plan, Source source, Clock clock) {
        return run(plan, source, new Context(clock), ignored -> {});
    }

    static JsonNode run(JsonNode plan, Source source, Context context) {
        return run(plan, source, context, ignored -> {});
    }

    private static JsonNode run(JsonNode input, Source source, Context context, Consumer<JsonNode> checkpoint) {
        JsonNode plan = validatePlan(input);
        ObjectNode output = JSON.createObjectNode().put("runId", plan.get("runId").asText());
        var cases = output.putArray("cases");
        for (JsonNode item : plan.get("cases")) cases.add(notRun(item));
        checkpoint.accept(output);
        // Construction does not request data; only source.execute below is a transport boundary.
        var localProperties = new TushareProperties(true, URI.create("https://api.tushare.pro"),
                new TushareProperties.Credential(""), Duration.ofSeconds(5), Duration.ofSeconds(120), 67_108_864, Duration.ofSeconds(2));
        var policies = new TushareBatchPolicies(new TushareProClient(new TushareRestClientFactory().create(localProperties), localProperties), DEFINITIONS);
        for (int index = 0; index < cases.size(); index++) {
            ObjectNode evidence = (ObjectNode)cases.get(index);
            executeCase(plan.get("cases").get(index), evidence, source, policies, context);
            checkpoint.accept(output);
            if (evidence.path("status").asText().equals("FAILED")) { context.stop(); break; }
        }
        return output;
    }

    private static void executeCase(JsonNode item, ObjectNode evidence, Source source, TushareBatchPolicies policies, Context context) {
        int before = context.requestCount();
        ObjectNode currentNode = null;
        var dates = new TreeSet<String>();
        var split = new SplitSummary();
        boolean closedCalendar = false;
        Integer calendarOpenDayCount = null;
        var calendarProjection = new CalendarProjection();
        String api = item.get("apiName").asText();
        var projection = new Projection(api, item.get("mode").asText().equals("RANGE"));
        DatasetDefinition definition = definition(api);
        Policy policy = productionPolicies().get(definition.datasetKey().apiName());
        Map<String,Object> params = parameters(item.get("params"));
        boolean ranged = item.get("mode").asText().equals("RANGE");
        boolean splitMainbz = ranged && api.equals("fina_mainbz");
        DatasetDefinition extendedDefinition = ranged ? rangeDefinition(definition) : definition;
        boolean bjTrading = ranged && policy.planningMode() == TRADING_DAYS
                && ((String)params.get("ts_code")).endsWith(".BJ");
        String dateColumn = ranged ? policy.outputDateColumn() : singleDateColumn(definition);
        try {
            context.check();
            DateRange range = ranged ? new DateRange(inputDate(item.get("start")), inputDate(item.get("end"))) : null;
            var ranges = new ArrayList<DateRange>();
            if (!ranged) ranges.add(null);
            else if (policy.planningMode() == NATIVE_RANGE) ranges.add(range);
            else if (policy.planningMode() == CALENDAR_DAYS) {
                for (LocalDate day=range.start(); !day.isAfter(range.end()); day=day.plusDays(1)) ranges.add(new DateRange(day,day));
            } else {
                // ISSUE-021 candidate only: official calendar reference; production BJ remains closed.
                if (!bjTrading) policies.sourceParameters(definition.datasetKey().apiName(), params, range);
                String stock = (String)params.get("ts_code");
                String exchange = stock.endsWith(".SZ") ? "SZSE" : "SSE";
                var calendarParams = Map.<String,Object>of("exchange",exchange,"start_date",format(range.start()),"end_date",format(range.end()));
                DownloadEnvelope calendar = source.execute(definition("trade_cal"), calendarParams, context);
                for (LocalDate day : calendarProjection.read(range,exchange,calendar)) ranges.add(new DateRange(day,day));
                calendarOpenDayCount = ranges.size();
                closedCalendar = ranges.isEmpty();
                if (closedCalendar) evidence.put("expectedCoverage","COMPLETE_CLOSED_CALENDAR");
            }
            if (splitMainbz) {
                evidence.put("expectedCoverage","ACCEPTED_ROW_LIMIT_COVERAGE");
                ObjectNode root=node(range,evidence.withArray("batchNodes").size(),null,"NOT_RUN");
                evidence.withArray("batchNodes").add(root);
                executeMainbzRange(root,range,evidence,source,policies,context,extendedDefinition,
                        definition,params,dateColumn,dates,projection,split);
            } else for (DateRange slice : ranges) {
                context.check();
                Map<String,Object> request = !ranged || (api.equals("trade_cal") && "BSE".equals(params.get("exchange")))
                        ? params : bjTrading ? Map.of("ts_code",params.get("ts_code"),"trade_date",format(slice.start()))
                        : policies.sourceParameters(definition.datasetKey().apiName(),params,slice);
                currentNode = node(slice, evidence.withArray("batchNodes").size(),null,"RUNNING");
                evidence.withArray("batchNodes").add(currentNode);
                DownloadEnvelope envelope = source.execute(ranged ? extendedDefinition : definition,request,context);
                if (api.equals("trade_cal")) {
                    DateRange calendarRange = ranged ? slice : singleDateRange(definition,request);
                    calendarOpenDayCount = calendarProjection.read(calendarRange,(String)params.get("exchange"),envelope).size();
                    closedCalendar = calendarOpenDayCount == 0;
                }
                if (ranged) policies.assess(definition.datasetKey().apiName(),slice,validationEnvelope(definition,extendedDefinition,envelope));
                else validateSingle(definition,request,envelope);
                if (dateColumn != null) {
                    int column = envelope.fields().indexOf(dateColumn);
                    for (List<Object> row : envelope.data()) {
                        Object value = row.get(column);
                        if (value != null) dates.add(format(date(value,SOURCE_PAYLOAD_INVALID)));
                    }
                }
                projection.observe(slice,envelope);
                if (ranged && policy.documentedRowLimit() != null && envelope.rowCount() >= policy.documentedRowLimit()) split.candidateLimitReached = true;
                currentNode.put("status","SUCCEEDED").put("sourceRowCount",envelope.rowCount());
                currentNode = null;
            }
            summarize(evidence,context.requestCount()-before);
            boolean empty = evidence.path("sourceRowCount").asLong() == 0;
            evidence.put("status", (!closedCalendar && empty) || (split.candidateLimitReached && !splitMainbz) ? "EVIDENCE_MISSING" : "PASS");
        } catch (Exception failure) {
            String code = errorCode(failure);
            if (currentNode != null) currentNode.put("status","FAILED").put("errorCode",code);
            evidence.put("status","FAILED").put("errorCode",code);
            summarize(evidence,context.requestCount()-before);
        }
        String review = "SOURCE validationStatus=" + evidence.path("status").asText() + "; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=" + (dateColumn == null ? "none" : dateColumn)
                + "; datePredicate=" + (dateColumn == null ? "none" : ranged || params.containsKey("start_date") ? "closed interval" : "exact date")
                + "; actualDates=" + String.join(",",dates)
                + "; uniqueDateCount=" + dates.size() + "; min=" + (dates.isEmpty()?"none":dates.first())
                + "; max=" + (dates.isEmpty()?"none":dates.last())
                + "; candidateRowLimit=" + (policy == null || policy.documentedRowLimit() == null ? "UNKNOWN" : policy.documentedRowLimit())
                + "; candidateLimitReached=" + split.candidateLimitReached;
        if (splitMainbz) review += "; splitRule=ROW_LIMIT=100; splitParentCount="
                + split.parentCount + "; unresolvedFullLeafCount=" + split.unresolvedFullLeafCount;
        review += projection.review(evidence.path("succeededLeafCount").asInt() > 0);
        review += calendarProjection.review();
        if (api.equals("trade_cal") || (ranged && api.equals("top_list"))) review += "; calendarOpenDayCount=" + (calendarOpenDayCount == null ? "unconfirmed" : calendarOpenDayCount) + "; closedCalendar=" + closedCalendar;
        evidence.put("reviewMethod",review);
        evidence.withArray("evidencePaths").add("source-evidence.json#cases/" + item.get("caseId").asText());
    }

    private static void executeMainbzRange(ObjectNode node,DateRange range,ObjectNode evidence,Source source,
            TushareBatchPolicies policies,Context context,DatasetDefinition extendedDefinition,
            DatasetDefinition definition,Map<String,Object> params,String dateColumn,Set<String> dates,
            Projection projection,SplitSummary split) {
        context.check();
        node.put("status","RUNNING");
        try {
            Map<String,Object> request=policies.sourceParameters(definition.datasetKey().apiName(),params,range);
            DownloadEnvelope envelope=source.execute(extendedDefinition,request,context);
            policies.assess(definition.datasetKey().apiName(),range,validationEnvelope(definition,extendedDefinition,envelope));
            if(envelope.rowCount()>=100) {
                split.candidateLimitReached=true;
                if(range.start().equals(range.end())) {
                    split.unresolvedFullLeafCount++;
                    node.put("status","FAILED").put("errorCode",BATCH_COMPLETENESS_UNCONFIRMED.name());
                    throw failure(BATCH_COMPLETENESS_UNCONFIRMED);
                }
                node.put("status","SPLIT");
                split.parentCount++;
                List<DateRange> halves=DateRangePlanner.split(range);
                ObjectNode left=node(halves.get(0),evidence.withArray("batchNodes").size(),node.path("batchId").asText(),"NOT_RUN");
                evidence.withArray("batchNodes").add(left);
                ObjectNode right=node(halves.get(1),evidence.withArray("batchNodes").size(),node.path("batchId").asText(),"NOT_RUN");
                evidence.withArray("batchNodes").add(right);
                executeMainbzRange(left,halves.get(0),evidence,source,policies,context,extendedDefinition,
                        definition,params,dateColumn,dates,projection,split);
                executeMainbzRange(right,halves.get(1),evidence,source,policies,context,extendedDefinition,
                        definition,params,dateColumn,dates,projection,split);
                return;
            }
            int column=envelope.fields().indexOf(dateColumn);
            for(List<Object> row:envelope.data()) {
                Object value=row.get(column);
                if(value!=null) dates.add(format(date(value,SOURCE_PAYLOAD_INVALID)));
            }
            projection.observe(range,envelope);
            node.put("status","SUCCEEDED").put("sourceRowCount",envelope.rowCount());
        } catch(Exception failure) {
            if(node.path("status").asText().equals("RUNNING")) node.put("status","FAILED").put("errorCode",errorCode(failure));
            throw failure;
        }
    }

    private static String errorCode(Exception failure) {
        return failure instanceof TensorException classified ? classified.code().name() : SOURCE_UNAVAILABLE.name();
    }

    private static final class SplitSummary {
        private boolean candidateLimitReached;
        private int parentCount;
        private int unresolvedFullLeafCount;
    }

    private static DatasetDefinition rangeDefinition(DatasetDefinition definition) {
        List<String> extra = switch (definition.datasetKey().apiName().value()) {
            case "stk_holdertrade" -> List.of("begin_date","close_date");
            case "disclosure_date" -> List.of("modify_date");
            default -> List.of();
        };
        if (extra.isEmpty()) return definition;
        var columns = new ArrayList<>(definition.columns());
        for (String name : extra) columns.add(new ColumnDefinition(name,name,LogicalType.STRING,true,
                columns.size(),4096,null,null,List.of(),false));
        return new DatasetDefinition(definition.datasetKey(),definition.displayName(),definition.category(),definition.queryMode(),
                definition.parameters(),definition.tableName(),columns,definition.businessKey(),definition.filters(),definition.fixedColumn(),definition.batchSize());
    }

    private static DownloadEnvelope validationEnvelope(DatasetDefinition original,DatasetDefinition extended,DownloadEnvelope envelope) {
        if (original == extended) return envelope;
        // Validate the entire selected response before hiding auxiliary columns from the unchanged production policy.
        validateEnvelope(original.datasetKey().apiName(),extended.columns().stream().map(ColumnDefinition::name).toList(),envelope);
        int width=original.columns().size();
        return new DownloadEnvelope(envelope.pluginId(),envelope.apiName(),envelope.params(),envelope.fields().subList(0,width),
                envelope.rowCount(),envelope.data().stream().map(row->row.subList(0,width)).toList(),envelope.status(),envelope.error());
    }

    private static void validateSingle(DatasetDefinition definition, Map<String,Object> params, DownloadEnvelope envelope) {
        validateEnvelope(definition.datasetKey().apiName(),definition.columns().stream().map(ColumnDefinition::name).toList(),envelope);
        if (!params.equals(envelope.params())) throw failure(SOURCE_RANGE_MISMATCH);
        for (String scope : List.of("ts_code","exchange","exchange_id")) {
            if (!params.containsKey(scope)) continue;
            int index = envelope.fields().indexOf(scope);
            if (index < 0) throw failure(SOURCE_RANGE_MISMATCH);
            for (List<Object> row : envelope.data()) if (!params.get(scope).equals(row.get(index))) throw failure(SOURCE_RANGE_MISMATCH);
        }
        DateRange requested = singleDateRange(definition,params);
        if (requested != null) {
            int column = envelope.fields().indexOf(singleDateColumn(definition));
            if (column < 0) throw failure(SOURCE_PAYLOAD_INVALID);
            for (List<Object> row : envelope.data()) within(date(row.get(column),SOURCE_PAYLOAD_INVALID),requested);
        }
    }

    private static String singleDateColumn(DatasetDefinition definition) {
        return switch (definition.queryMode()) {
            case snapshot -> null;
            case trade_date -> "trade_date";
            case ann_date -> "ann_date";
            case date_range -> switch (definition.datasetKey().apiName().value()) {
                case "trade_cal" -> "cal_date";
                case "new_share" -> "ipo_date";
                default -> throw invalid();
            };
        };
    }

    private static DateRange singleDateRange(DatasetDefinition definition,Map<String,Object> params) {
        return switch (definition.queryMode()) {
            case snapshot -> null;
            case date_range -> new DateRange(date(params.get("start_date"),PARAM_INVALID),date(params.get("end_date"),PARAM_INVALID));
            case trade_date, ann_date -> {
                LocalDate day = date(params.get(definition.queryMode().name()),PARAM_INVALID);
                yield new DateRange(day,day);
            }
        };
    }

    private static ObjectNode notRun(JsonNode item) {
        ObjectNode result = JSON.createObjectNode();
        for (String key : List.of("caseId","apiName","mode","params","dateAxis")) result.set(key,item.get(key).deepCopy());
        result.put("phase","SOURCE").put("status","NOT_RUN").put("expectedCoverage","SOURCE_SEMANTICS_ONLY_COMPLETENESS_UNCONFIRMED");
        var refs = new ArrayList<String>(); for (JsonNode ref : item.get("evidenceRefs")) refs.add(ref.asText());
        result.put("expectedCoverageSource",refs.isEmpty()?"Current YAML SINGLE contract":String.join("; ",refs));
        for (String key : List.of("errorCode","taskId","submissionId","requestCount","leafCount","succeededLeafCount","failedLeafCount","emptyLeafCount","sourceRowCount","insertedRows","updatedRows","sqlBeforeKeyCount","sqlAfterKeyCount","ownershipSummary","businessKeyDigest","reviewMethod")) result.putNull(key);
        result.putArray("batchNodes"); result.putArray("evidencePaths"); return result;
    }

    private static ObjectNode node(DateRange range,int index,String parent,String status) {
        ObjectNode node = JSON.createObjectNode().put("batchId","source-"+index).put("status",status);
        for (String key : List.of("parentBatchId","sourceRowCount","insertedRows","updatedRows","errorCode","start","end")) node.putNull(key);
        if(parent!=null) node.put("parentBatchId",parent);
        if (range != null) node.put("start",format(range.start())).put("end",format(range.end()));
        return node;
    }

    private static void summarize(ObjectNode evidence,int requests) {
        int leaves=0,succeeded=0,failed=0,empty=0; long rows=0;
        for (JsonNode node : evidence.path("batchNodes")) {
            if (node.path("status").asText().equals("SPLIT")) continue;
            leaves++;
            if (node.path("status").asText().equals("SUCCEEDED")) { succeeded++; rows+=node.path("sourceRowCount").asLong(); if (node.path("sourceRowCount").asLong()==0) empty++; }
            if (node.path("status").asText().equals("FAILED")) failed++;
        }
        evidence.put("requestCount",requests).put("leafCount",leaves).put("succeededLeafCount",succeeded)
                .put("failedLeafCount",failed).put("emptyLeafCount",empty).put("sourceRowCount",rows);
    }

    private static final class CalendarProjection {
        private String exchange;
        private long responseRows;
        private List<LocalDate> openDays;

        private List<LocalDate> read(DateRange range,String requestedExchange,DownloadEnvelope envelope) {
            validateEnvelope(new com.akkc.tensor.plugin.api.model.ApiName("trade_cal"),
                    List.of("exchange","cal_date","is_open","pretrade_date"),envelope);
            exchange=requestedExchange;
            responseRows=envelope.rowCount();
            openDays=TushareTradeCalendar.openDays(range,exchange,envelope);
            return openDays;
        }

        private String review() {
            return exchange==null ? "" : "; calendarExchange="+exchange+"; calendarResponseRowCount="+responseRows
                    +"; calendarOpenDates="+(openDays==null ? "unconfirmed"
                    : String.join(",",openDays.stream().map(TushareBatchPolicies::format).toList()))+";";
        }
    }

    private static final class Projection {
        private final String api;
        private final boolean enabled;
        private final MainbzProjection mainbz;
        private final EventProjection event;
        private final SlbProjection slb;
        private final DateComparison actualAnnouncement;
        private final boolean reportPeriod;
        private final Set<String> stocks = new HashSet<>();
        private int valid;
        private int unavailable;
        private int different;
        private int outside;
        private int suspensions,resumptions,unknownSuspensions,intraday;

        private Projection(String api,boolean ranged) {
            this.api=api;
            actualAnnouncement=ranged && api.equals("cashflow") ? new DateComparison() : null;
            reportPeriod=Set.of("fina_indicator","top10_holders","top10_floatholders").contains(api);
            slb=ranged && Set.of("slb_len","slb_sec","slb_sec_detail").contains(api) ? new SlbProjection(api) : null;
            mainbz=api.equals("fina_mainbz") ? new MainbzProjection() : null;
            event=(ranged && Set.of("block_trade","stk_holdertrade","pledge_detail","disclosure_date","stk_managers").contains(api))
                    || api.equals("pledge_detail") ? new EventProjection(api,ranged) : null;
            enabled=ranged && (reportPeriod || Set.of("income","balancesheet","cashflow","fina_audit","express",
                    "repurchase","stk_holdernumber","new_share","suspend_d").contains(api));
        }

        private void observe(DateRange range,DownloadEnvelope envelope) {
            if (mainbz!=null) mainbz.observe(envelope);
            if (event!=null) event.observe(range,envelope);
            if (slb!=null) slb.observe(envelope);
            if (!enabled) return;
            if (api.equals("suspend_d")) {
                for(List<Object> row:envelope.data()) {
                    Object type=EventProjection.value(envelope,row,"suspend_type");
                    if("S".equals(type)) suspensions++; else if("R".equals(type)) resumptions++; else unknownSuspensions++;
                    if(EventProjection.nonblank(EventProjection.value(envelope,row,"suspend_timing"))) intraday++;
                }
                return;
            }
            if (api.equals("repurchase")) {
                int column=envelope.fields().indexOf("ts_code");
                for (List<Object> row:envelope.data()) {
                    Object value=row.get(column);
                    if (value instanceof String stock && stock.matches("[0-9]{6}\\.(SZ|SH|BJ)")) {
                        valid++;stocks.add(stock);
                    } else unavailable++;
                }
                return;
            }
            int annColumn=envelope.fields().indexOf(api.equals("new_share") ? "ipo_date" : "ann_date");
            int endColumn=envelope.fields().indexOf(api.equals("new_share") ? "issue_date" : "end_date");
            for (List<Object> row:envelope.data()) {
                LocalDate ann=projectedDate(row.get(annColumn)),end=projectedDate(row.get(endColumn));
                if(actualAnnouncement!=null) actualAnnouncement.observe(ann,projectedDate(EventProjection.value(envelope,row,"f_ann_date")),range);
                if (ann==null || end==null) { unavailable++;continue; }
                valid++;
                if (!reportPeriod) {
                    if (!ann.equals(end)) different++;
                    if (within(ann,range) && !within(end,range)) outside++;
                } else if (within(end,range) && !within(ann,range)) outside++;
            }
        }

        private String review(boolean observedResponse) {
            if (mainbz!=null && observedResponse) return mainbz.review();
            if (event!=null && observedResponse) return event.review();
            if (slb!=null && observedResponse) return slb.review();
            if (!enabled || !observedResponse) return "";
            if (api.equals("suspend_d")) return "; suspensionRows="+suspensions+"; resumptionRows="+resumptions
                    +"; suspensionTypeUnavailableRows="+unknownSuspensions+"; intradayTimingPresentRows="+intraday;
            if (Set.of("income","balancesheet","cashflow","fina_audit","express","stk_holdernumber").contains(api)) {
                String review="; dateComparisonValidRows="+valid+"; dateComparisonUnavailableRows="+unavailable
                        +"; annDateDifferentFromEndDateRows="+different+"; annInRangeEndOutsideRows="+outside;
                if(actualAnnouncement!=null) review+=actualAnnouncement.review("cashflowActualAnnouncement")
                        +"; cashflowActualAnnouncementDifferentRows="+actualAnnouncement.different
                        +"; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows="+actualAnnouncement.outside;
                return review;
            }
            if (api.equals("new_share")) return "; dateComparisonValidRows="+valid+"; dateComparisonUnavailableRows="+unavailable
                    +"; ipoDateDifferentFromIssueDateRows="+different+"; ipoInRangeIssueOutsideRows="+outside;
            if (reportPeriod) return "; dateComparisonValidRows="+valid+"; dateComparisonUnavailableRows="+unavailable
                    +"; endInRangeAnnOutsideRows="+outside;
            return "; validStockCodeRows="+valid+"; unavailableStockCodeRows="+unavailable+"; distinctStockCodeCount="+stocks.size();
        }

        private static LocalDate projectedDate(Object value) {
            if (!(value instanceof String text) || !text.matches("[0-9]{8}")) return null;
            try { return LocalDate.parse(text,DateTimeFormatter.BASIC_ISO_DATE); }
            catch (DateTimeException ignored) { return null; }
        }

        private static boolean within(LocalDate date,DateRange range) {
            return !date.isBefore(range.start()) && !date.isAfter(range.end());
        }
    }

    /** Safe SOURCE observations only; no raw business keys, parties, amounts or correction text leave this class. */
    private static final class EventProjection {
        private final String api;
        private final boolean ranged;
        private final Set<String> dates=new TreeSet<>(),recordDigests=new TreeSet<>();
        private final Set<List<Object>> blockKeys=new HashSet<>(),parties=new HashSet<>();
        private final Map<List<Object>,Set<List<Object>>> blockGroups=new HashMap<>();
        private final Map<String,Set<String>> disclosureKeys=new TreeMap<>();
        private final Map<String,DateComparison> comparisons=new LinkedHashMap<>();
        private int validKey,unavailableKey,unavailableDate,modifyPresent,modifyParseable,modifyUnavailable,modifyMultiple;

        private EventProjection(String api,boolean ranged) {
            this.api=api;this.ranged=ranged;
            for(String companion:switch(api) {
                case "stk_holdertrade" -> List.of("Begin","Close");
                case "stk_managers" -> List.of("Begin","End");
                case "pledge_detail" -> List.of("Start","End","Release");
                case "disclosure_date" -> List.of("AnnEnd","PreActual");
                default -> List.<String>of();
            }) comparisons.put(companion,new DateComparison());
        }

        private void observe(DateRange range,DownloadEnvelope envelope) {
            for(List<Object> row:envelope.data()) {
                if(api.equals("block_trade")) { blockTrade(envelope,row);continue; }
                LocalDate ann=Projection.projectedDate(value(envelope,row,"ann_date"));
                if(!ranged) { if(ann!=null) dates.add(format(ann));continue; }
                if(api.equals("disclosure_date")) { disclosure(envelope,row,ann);continue; }
                for(var comparison:comparisons.entrySet()) comparison.getValue().observe(ann,
                        Projection.projectedDate(value(envelope,row,comparison.getKey().toLowerCase(Locale.ROOT)+"_date")),range);
            }
        }

        private void blockTrade(DownloadEnvelope envelope,List<Object> row) {
            Object stock=value(envelope,row,"ts_code"),trade=value(envelope,row,"trade_date"),
                    buyer=value(envelope,row,"buyer"),seller=value(envelope,row,"seller");
            BigDecimal price=decimal(value(envelope,row,"price")),volume=decimal(value(envelope,row,"vol"));
            if(!nonblank(stock) || !nonblank(buyer) || !nonblank(seller) || Projection.projectedDate(trade)==null
                    || price==null || volume==null) { unavailableKey++;return; }
            validKey++;
            List<Object> key=List.of(trade,stock,buyer,seller,price,volume);
            blockKeys.add(key);parties.add(List.of(buyer,seller));
            blockGroups.computeIfAbsent(List.of(stock,trade),ignored->new HashSet<>()).add(key);
        }

        private void disclosure(DownloadEnvelope envelope,List<Object> row,LocalDate ann) {
            LocalDate end=Projection.projectedDate(value(envelope,row,"end_date"));
            if(end==null) unavailableDate++; else dates.add(format(end));
            comparisons.get("AnnEnd").observe(ann,end,null);
            comparisons.get("PreActual").observe(Projection.projectedDate(value(envelope,row,"pre_date")),
                    Projection.projectedDate(value(envelope,row,"actual_date")),null);
            Object stock=value(envelope,row,"ts_code");
            if(end==null || !nonblank(stock)) unavailableKey++;
            else {
                validKey++;
                String key=digest(List.of(stock,format(end))),record=digest(row.subList(0,5));
                disclosureKeys.computeIfAbsent(key,ignored->new HashSet<>()).add(record);
                recordDigests.add(record);
            }
            Object modify=value(envelope,row,"modify_date");
            if(nonblank(modify)) modifyPresent++;
            Set<LocalDate> modified=modificationDates(modify);
            if(modified==null) modifyUnavailable++;
            else { modifyParseable++;if(modified.size()>1) modifyMultiple++; }
        }

        private String review() {
            if(!ranged) return "; pledgeAnnouncementDates="+String.join(",",dates);
            if(api.equals("block_trade")) return "; blockTradeBusinessKeyValidRows="+validKey
                    +"; blockTradeBusinessKeyUnavailableRows="+unavailableKey+"; blockTradeDistinctBusinessKeyCount="+blockKeys.size()
                    +"; blockTradeSameStockDateMultipleKeyGroupCount="+blockGroups.values().stream().filter(v->v.size()>1).count()
                    +"; blockTradeDistinctBuyerSellerPairCount="+parties.size();
            if(api.equals("disclosure_date")) return "; disclosureReportDates="+String.join(",",dates)
                    +"; disclosureReportDateUnavailableRows="+unavailableDate+"; disclosureBusinessKeyValidRows="+validKey
                    +"; disclosureBusinessKeyUnavailableRows="+unavailableKey+"; disclosureDistinctBusinessKeyCount="+disclosureKeys.size()
                    +"; disclosureBusinessKeyDigestSha256="+digest(disclosureKeys.keySet())+"; disclosureRecordDigestSha256="+digest(recordDigests)
                    +"; disclosureSameKeyDifferentRecordCount="+disclosureKeys.values().stream().filter(v->v.size()>1).count()
                    +comparisons.get("AnnEnd").review("disclosureAnnEnd")
                    +"; disclosureAnnDateDifferentFromEndDateRows="+comparisons.get("AnnEnd").different
                    +comparisons.get("PreActual").review("disclosurePreActual")
                    +"; disclosurePreDateDifferentFromActualDateRows="+comparisons.get("PreActual").different
                    +"; disclosureModifyDatePresentRows="+modifyPresent+"; disclosureModifyDateParseableRows="+modifyParseable
                    +"; disclosureModifyDateUnavailableRows="+modifyUnavailable+"; disclosureModifyDateMultipleDatesRows="+modifyMultiple;
            String prefix=api.equals("stk_holdertrade")?"holder":api.equals("stk_managers")?"manager":"pledge";
            var result=new StringBuilder();
            for(var entry:comparisons.entrySet()) result.append(entry.getValue().review(prefix+entry.getKey()))
                    .append("; ").append(prefix).append("AnnDateDifferentFrom").append(entry.getKey()).append("DateRows=").append(entry.getValue().different)
                    .append("; ").append(prefix).append("AnnInRange").append(entry.getKey()).append("OutsideRows=").append(entry.getValue().outside);
            return result.toString();
        }

        private static Object value(DownloadEnvelope envelope,List<Object> row,String column) {
            return row.get(envelope.fields().indexOf(column));
        }

        private static boolean nonblank(Object value) { return value instanceof String text && !text.isBlank(); }

        private static BigDecimal decimal(Object value) {
            if(!(value instanceof Number) && !(value instanceof String)) return null;
            try { return new BigDecimal(value.toString()).stripTrailingZeros(); }
            catch(NumberFormatException | ArithmeticException ignored) { return null; }
        }

        private static Set<LocalDate> modificationDates(Object value) {
            if(!nonblank(value)) return null;
            var dates=new HashSet<LocalDate>();
            for(String text:((String)value).split(",",-1)) {
                text=text.trim();
                if(text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) text=text.replace("-","");
                LocalDate date=Projection.projectedDate(text);
                if(date==null) return null;
                dates.add(date);
            }
            return dates;
        }

        private static String digest(Object value) {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(value))); }
            catch(Exception ignored) { throw invalid(); }
        }
    }

    /** Original YAML keys only; numeric values remain in memory and only counts/digests leave the probe. */
    private static final class SlbProjection {
        private final String api;
        private final Set<String> keys=new TreeSet<>();
        private final Set<BigDecimal> tenors=new HashSet<>(),rates=new HashSet<>();
        private final Set<List<BigDecimal>> pairs=new HashSet<>();
        private final Map<List<Object>,Set<Object>> dateKeys=new HashMap<>(),dateTenors=new HashMap<>(),tenorRates=new HashMap<>();
        private int valid,unavailable;

        private SlbProjection(String api) { this.api=api; }

        private void observe(DownloadEnvelope envelope) {
            for(List<Object> row:envelope.data()) {
                Object trade=EventProjection.value(envelope,row,"trade_date");
                if(Projection.projectedDate(trade)==null) { unavailable++;continue; }
                var key=new ArrayList<Object>();key.add(trade);
                var group=new ArrayList<Object>();group.add(trade);
                BigDecimal tenor=null,rate=null;
                if(api.equals("slb_len")) {
                    BigDecimal balance=EventProjection.decimal(EventProjection.value(envelope,row,"ob"));
                    if(balance==null) { unavailable++;continue; }
                    key.add(balance);
                } else {
                    Object stock=EventProjection.value(envelope,row,"ts_code");
                    if(!EventProjection.nonblank(stock)) { unavailable++;continue; }
                    key.add(stock);group.add(stock);
                    if(api.equals("slb_sec_detail")) {
                        tenor=EventProjection.decimal(EventProjection.value(envelope,row,"tenor"));
                        rate=EventProjection.decimal(EventProjection.value(envelope,row,"fee_rate"));
                        if(tenor==null || tenor.signum()<=0 || tenor.scale()>0 || rate==null) { unavailable++;continue; }
                        key.add(tenor);key.add(rate);
                    }
                }
                valid++;
                String digest=EventProjection.digest(key);keys.add(digest);
                dateKeys.computeIfAbsent(group,ignored->new HashSet<>()).add(digest);
                if(tenor!=null) {
                    tenors.add(tenor);rates.add(rate);pairs.add(List.of(tenor,rate));
                    dateTenors.computeIfAbsent(group,ignored->new HashSet<>()).add(tenor);
                    var tenorGroup=new ArrayList<>(group);tenorGroup.add(tenor);
                    tenorRates.computeIfAbsent(tenorGroup,ignored->new HashSet<>()).add(rate);
                }
            }
        }

        private String review() {
            return "; slbBusinessKeyValidRows="+valid+"; slbBusinessKeyUnavailableRows="+unavailable
                    +"; slbDistinctBusinessKeyCount="+keys.size()+"; slbDuplicateBusinessKeyRows="+(valid-keys.size())
                    +"; slbSameDateMultipleKeyGroupCount="+multiple(dateKeys)+"; slbBusinessKeyDigestSha256="+EventProjection.digest(keys)
                    +(api.equals("slb_sec_detail") ? "; slbDistinctTenorCount="+tenors.size()+"; slbDistinctFeeRateCount="+rates.size()
                    +"; slbDistinctTenorFeeRatePairCount="+pairs.size()+"; slbSameStockDateMultipleTenorGroupCount="+multiple(dateTenors)
                    +"; slbSameStockDateTenorMultipleFeeRateGroupCount="+multiple(tenorRates) : "");
        }

        private static long multiple(Map<List<Object>,Set<Object>> groups) {
            return groups.values().stream().filter(values->values.size()>1).count();
        }
    }

    private static final class DateComparison {
        private int valid,unavailable,different,outside;

        private void observe(LocalDate primary,LocalDate companion,DateRange range) {
            if(primary==null || companion==null) { unavailable++;return; }
            valid++;
            if(!primary.equals(companion)) different++;
            if(range!=null && Projection.within(primary,range) && !Projection.within(companion,range)) outside++;
        }

        private String review(String prefix) {
            return "; "+prefix+"DateComparisonValidRows="+valid+"; "+prefix+"DateComparisonUnavailableRows="+unavailable;
        }
    }

    private static final class MainbzProjection {
        private final Map<String,Integer> types=new HashMap<>();
        private final Set<String> dates=new TreeSet<>();
        private final Map<List<Object>,Set<String>> keys=new HashMap<>();
        private int unavailableType,unavailableDate,validKey,unavailableKey;

        private void observe(DownloadEnvelope envelope) {
            int typeColumn=envelope.fields().indexOf("bz_code"),dateColumn=envelope.fields().indexOf("end_date");
            int[] keyColumns=List.of("ts_code","end_date","bz_item","curr_type").stream()
                    .mapToInt(envelope.fields()::indexOf).toArray();
            for (List<Object> row:envelope.data()) {
                Object value=row.get(typeColumn);
                String type=value instanceof String text && Set.of("P","D","I").contains(text) ? text : null;
                if(type==null) unavailableType++; else types.merge(type,1,Integer::sum);
                LocalDate reportDate=Projection.projectedDate(row.get(dateColumn));
                if(reportDate==null) unavailableDate++; else dates.add(format(reportDate));
                var key=new ArrayList<Object>();
                for(int column:keyColumns) key.add(row.get(column));
                if(reportDate==null || key.stream().anyMatch(v->!(v instanceof String text) || text.isBlank())) {
                    unavailableKey++;
                } else {
                    validKey++;
                    Set<String> categories=keys.computeIfAbsent(key,ignored->new HashSet<>());
                    if(type!=null) categories.add(type);
                }
            }
        }

        private String review() {
            return "; mainbzTypePRows="+types.getOrDefault("P",0)+"; mainbzTypeDRows="+types.getOrDefault("D",0)
                    +"; mainbzTypeIRows="+types.getOrDefault("I",0)+"; mainbzTypeUnavailableRows="+unavailableType
                    +"; mainbzReportDates="+String.join(",",dates)+"; mainbzReportDateUnavailableRows="+unavailableDate
                    +"; mainbzBusinessKeyValidRows="+validKey+"; mainbzBusinessKeyUnavailableRows="+unavailableKey
                    +"; mainbzDistinctBusinessKeyCount="+keys.size()
                    +"; mainbzCrossTypeBusinessKeyCount="+keys.values().stream().filter(v->v.size()>1).count();
        }
    }

    static final class Context implements BatchCallContext {
        private final Clock clock;
        private final Instant deadline;
        private volatile boolean stopped;
        private int requests;
        Context(Clock clock) { this.clock=clock; deadline=clock.instant().plus(Duration.ofMinutes(30)); }
        public Instant deadline(){return deadline;}
        public boolean stopRequested(){return stopped || Thread.currentThread().isInterrupted();}
        public synchronized void beforeRequest(){check(); if(requests>=5000) throw failure(TASK_LIMIT_EXCEEDED); requests++;}
        synchronized int requestCount(){return requests;}
        void stop(){stopped=true;}
        void check(){if(stopRequested()) throw failure(EXECUTION_INTERRUPTED); if(!clock.instant().isBefore(deadline)) throw failure(TASK_LIMIT_EXCEEDED);}
    }

    private static DatasetDefinition definition(String api) {
        return DEFINITIONS.stream().filter(d->d.datasetKey().apiName().value().equals(api)).findFirst().orElseThrow(TushareRangeSourceProbe::invalid);
    }
    private static Map<String,Object> parameters(JsonNode params) {
        var result=new LinkedHashMap<String,Object>(); params.properties().forEach(e->result.put(e.getKey(),e.getValue().asText()));return Map.copyOf(result);
    }
    private static LocalDate inputDate(JsonNode value) {
        text(value); LocalDate date=date(value.textValue(),PARAM_INVALID); if(date.getYear()<2000 || date.getYear()>2100) throw invalid();return date;
    }
    private static void stock(JsonNode value){text(value);if(!value.textValue().matches("[0-9]{6}\\.(SZ|SH|BJ)")) throw invalid();}
    private static void text(JsonNode value){if(value==null || !value.isTextual() || value.textValue().isBlank()) throw invalid();}
    private static void exact(JsonNode value,Set<String> keys){if(value==null || !value.isObject() || value.size()!=keys.size()) throw invalid();for(String key:keys) if(!value.has(key)) throw invalid();}
    private static void safeStrings(JsonNode value){if(value==null) throw invalid();if(value.isTextual() && UNSAFE.matcher(value.textValue()).find()) throw invalid();if(value.isContainerNode()) value.forEach(TushareRangeSourceProbe::safeStrings);}
    private static String required(Map<String,String> env,String key){String value=env.get(key);if(value==null || value.isBlank()) throw invalid();return value;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid Tushare SOURCE probe configuration");}
}
