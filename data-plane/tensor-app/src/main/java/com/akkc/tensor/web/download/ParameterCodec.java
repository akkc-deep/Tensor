package com.akkc.tensor.web.download;

import static com.akkc.tensor.plugin.api.descriptor.ParameterType.*;
import static com.akkc.tensor.web.download.ParameterShape.of;

import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.web.download.DownloadParameters.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

record ParameterCodec<T extends DownloadParameters>(ParameterShape shape, Class<T> parameterType,
        Function<ParameterJsonReader, T> reader, Function<T, Map<String, Object>> writer) {
    T read(ParameterJsonReader json) {
        return reader.apply(json);
    }

    Map<String, Object> write(DownloadParameters parameters) {
        return writer.apply(parameterType.cast(parameters));
    }

    static List<ParameterCodec<?>> supported() {
        var tradeDate = field("trade_date", DATE);
        var annDate = field("ann_date", DATE);
        var tsCode = field("ts_code", TS_CODE);
        var exchange = enumeration("exchange", "SSE", "SZSE", "BSE");
        var start = new ParameterShape.Field("start_date", DATE_RANGE_MEMBER, true, null,
                Set.of(), null, "end_date");
        var end = new ParameterShape.Field("end_date", DATE_RANGE_MEMBER, true, null,
                Set.of(), null, "start_date");
        var scenario = new ParameterShape.Field("scenario", ENUM, true, "SUCCESS",
                Set.of("SUCCESS", "EMPTY", "SOURCE_FAILURE", "TYPE_FAILURE", "PERSISTENCE_FAILURE"), null, null);
        var optionalStock = new ParameterShape.Field("ts_code", TS_CODE, false, null, Set.of(), null, null);
        return List.of(
                new ParameterCodec<>(of(scenario, start, end), ScenarioDateRangeParameters.class,
                        json -> new ScenarioDateRangeParameters(json.nullableText("scenario"), json.nullableText("start_date"), json.nullableText("end_date")),
                        value -> values("scenario", value.scenario(), "start_date", value.startDate(), "end_date", value.endDate())),
                new ParameterCodec<>(of(scenario, optionalStock, start, end), ScenarioStockDateRangeParameters.class,
                        json -> new ScenarioStockDateRangeParameters(json.nullableText("scenario"), json.nullableText("ts_code"), json.nullableText("start_date"), json.nullableText("end_date")),
                        value -> values("scenario", value.scenario(), "ts_code", value.tsCode(), "start_date", value.startDate(), "end_date", value.endDate())),
                new ParameterCodec<>(of(), SnapshotParameters.class,
                        json -> new SnapshotParameters(), value -> values()),
                new ParameterCodec<>(of(annDate), AnnDateParameters.class,
                        json -> new AnnDateParameters(json.nullableText("ann_date")),
                        value -> values("ann_date", value.annDate())),
                new ParameterCodec<>(of(exchange), ExchangeParameters.class,
                        json -> new ExchangeParameters(json.nullableText("exchange")),
                        value -> values("exchange", value.exchange())),
                new ParameterCodec<>(of(exchange, start, end), ExchangeDateRangeParameters.class,
                        json -> new ExchangeDateRangeParameters(json.nullableText("exchange"),
                                json.nullableText("start_date"), json.nullableText("end_date")),
                        value -> values("exchange", value.exchange(), "start_date", value.startDate(),
                                "end_date", value.endDate())),
                new ParameterCodec<>(of(tsCode, start, end), TsCodeDateRangeParameters.class,
                        json -> new TsCodeDateRangeParameters(json.nullableText("ts_code"),
                                json.nullableText("start_date"), json.nullableText("end_date")),
                        value -> values("ts_code", value.tsCode(), "start_date", value.startDate(),
                                "end_date", value.endDate())),
                new ParameterCodec<>(of(enumeration("exchange_id", "SSE", "SZSE", "BSE"), start, end),
                        ExchangeIdDateRangeParameters.class,
                        json -> new ExchangeIdDateRangeParameters(json.nullableText("exchange_id"),
                                json.nullableText("start_date"), json.nullableText("end_date")),
                        value -> values("exchange_id", value.exchangeId(), "start_date", value.startDate(),
                                "end_date", value.endDate())),
                new ParameterCodec<>(of(enumeration("exchange_id", "SSE", "SZSE", "BSE"), tradeDate),
                        ExchangeTradeDateParameters.class,
                        json -> new ExchangeTradeDateParameters(json.nullableText("exchange_id"),
                                json.nullableText("trade_date")),
                        value -> values("exchange_id", value.exchangeId(), "trade_date", value.tradeDate())),
                new ParameterCodec<>(of(enumeration("hs_type", "SH", "SZ")), HsTypeParameters.class,
                        json -> new HsTypeParameters(json.nullableText("hs_type")),
                        value -> values("hs_type", value.hsType())),
                new ParameterCodec<>(of(enumeration("list_status", "L", "P", "D")), ListStatusParameters.class,
                        json -> new ListStatusParameters(json.nullableText("list_status")),
                        value -> values("list_status", value.listStatus())),
                new ParameterCodec<>(of(field("month", MONTH)), MonthParameters.class,
                        json -> new MonthParameters(json.nullableText("month")),
                        value -> values("month", value.month())),
                new ParameterCodec<>(of(start, end), DateRangeParameters.class,
                        json -> new DateRangeParameters(json.nullableText("start_date"), json.nullableText("end_date")),
                        value -> values("start_date", value.startDate(), "end_date", value.endDate())),
                new ParameterCodec<>(of(tradeDate), TradeDateParameters.class,
                        json -> new TradeDateParameters(json.nullableText("trade_date")),
                        value -> values("trade_date", value.tradeDate())),
                new ParameterCodec<>(of(tsCode), TsCodeParameters.class,
                        json -> new TsCodeParameters(json.nullableText("ts_code")),
                        value -> values("ts_code", value.tsCode())),
                new ParameterCodec<>(of(tsCode, annDate), TsCodeAnnDateParameters.class,
                        json -> new TsCodeAnnDateParameters(json.nullableText("ts_code"), json.nullableText("ann_date")),
                        value -> values("ts_code", value.tsCode(), "ann_date", value.annDate())),
                new ParameterCodec<>(of(new ParameterShape.Field("scenario", ENUM, true, "SUCCESS",
                        Set.of("SUCCESS", "EMPTY", "SOURCE_FAILURE", "TYPE_FAILURE", "PERSISTENCE_FAILURE"),
                        null, null)), ScenarioParameters.class,
                        json -> new ScenarioParameters(json.nullableText("scenario")),
                        value -> values("scenario", value.scenario())));
    }

    private static ParameterShape.Field field(String name, ParameterType type) {
        return new ParameterShape.Field(name, type, true, null, Set.of(), null, null);
    }

    private static ParameterShape.Field enumeration(String name, String... values) {
        return new ParameterShape.Field(name, ENUM, true, null, Set.of(values), null, null);
    }

    private static Map<String, Object> values(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return values;
    }
}
