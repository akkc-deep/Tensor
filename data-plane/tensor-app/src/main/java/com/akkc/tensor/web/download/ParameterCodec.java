package com.akkc.tensor.web.download;

import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.tushare.TushareConstants;
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
    private static final String SCENARIO = "scenario";
    private static final String SUCCESS_SCENARIO = "SUCCESS";

    T read(ParameterJsonReader json) {
        return reader.apply(json);
    }

    Map<String, Object> write(DownloadParameters parameters) {
        return writer.apply(parameterType.cast(parameters));
    }

    static List<ParameterCodec<?>> supported() {
        var tradeDate = field(DatasetFields.TRADE_DATE, DATE);
        var annDate = field(DatasetFields.ANN_DATE, DATE);
        var tsCode = field(DatasetFields.TS_CODE, TS_CODE);
        var exchange = enumeration(DatasetFields.EXCHANGE, TushareConstants.SSE, TushareConstants.SZSE, TushareConstants.BSE);
        var start = new ParameterShape.Field(DatasetFields.START_DATE, DATE_RANGE_MEMBER, true, null,
                Set.of(), null, DatasetFields.END_DATE);
        var end = new ParameterShape.Field(DatasetFields.END_DATE, DATE_RANGE_MEMBER, true, null,
                Set.of(), null, DatasetFields.START_DATE);
        return List.of(
                new ParameterCodec<>(of(), SnapshotParameters.class,
                        json -> new SnapshotParameters(), value -> values()),
                new ParameterCodec<>(of(annDate), AnnDateParameters.class,
                        json -> new AnnDateParameters(json.nullableText(DatasetFields.ANN_DATE)),
                        value -> values(DatasetFields.ANN_DATE, value.annDate())),
                new ParameterCodec<>(of(tsCode, exchange), ExchangeParameters.class,
                        json -> new ExchangeParameters(json.nullableText(DatasetFields.TS_CODE), json.nullableText(DatasetFields.EXCHANGE)),
                        value -> values(DatasetFields.TS_CODE, value.tsCode(), DatasetFields.EXCHANGE, value.exchange())),
                new ParameterCodec<>(of(exchange, start, end), ExchangeDateRangeParameters.class,
                        json -> new ExchangeDateRangeParameters(json.nullableText(DatasetFields.EXCHANGE),
                                json.nullableText(DatasetFields.START_DATE), json.nullableText(DatasetFields.END_DATE)),
                        value -> values(DatasetFields.EXCHANGE, value.exchange(), DatasetFields.START_DATE, value.startDate(),
                                DatasetFields.END_DATE, value.endDate())),
                new ParameterCodec<>(of(enumeration(DatasetFields.EXCHANGE_ID, TushareConstants.SSE, TushareConstants.SZSE, TushareConstants.BSE), tradeDate),
                        ExchangeTradeDateParameters.class,
                        json -> new ExchangeTradeDateParameters(json.nullableText(DatasetFields.EXCHANGE_ID),
                                json.nullableText(DatasetFields.TRADE_DATE)),
                        value -> values(DatasetFields.EXCHANGE_ID, value.exchangeId(), DatasetFields.TRADE_DATE, value.tradeDate())),
                new ParameterCodec<>(of(enumeration(DatasetFields.EXCHANGE_ID, TushareConstants.SSE, TushareConstants.SZSE, TushareConstants.BSE), start, end),
                        ExchangeIdDateRangeParameters.class,
                        json -> new ExchangeIdDateRangeParameters(json.nullableText(DatasetFields.EXCHANGE_ID),
                                json.nullableText(DatasetFields.START_DATE), json.nullableText(DatasetFields.END_DATE)),
                        value -> values(DatasetFields.EXCHANGE_ID, value.exchangeId(), DatasetFields.START_DATE, value.startDate(),
                                DatasetFields.END_DATE, value.endDate())),
                new ParameterCodec<>(of(tsCode, start, end), TsCodeDateRangeParameters.class,
                        json -> new TsCodeDateRangeParameters(json.nullableText(DatasetFields.TS_CODE),
                                json.nullableText(DatasetFields.START_DATE), json.nullableText(DatasetFields.END_DATE)),
                        value -> values(DatasetFields.TS_CODE, value.tsCode(), DatasetFields.START_DATE, value.startDate(),
                                DatasetFields.END_DATE, value.endDate())),
                new ParameterCodec<>(of(tsCode, enumeration(DatasetFields.LIST_STATUS, "L", "P", "D")), ListStatusParameters.class,
                        json -> new ListStatusParameters(json.nullableText(DatasetFields.TS_CODE), json.nullableText(DatasetFields.LIST_STATUS)),
                        value -> values(DatasetFields.TS_CODE, value.tsCode(), DatasetFields.LIST_STATUS, value.listStatus())),
                new ParameterCodec<>(of(start, end), DateRangeParameters.class,
                        json -> new DateRangeParameters(json.nullableText(DatasetFields.START_DATE), json.nullableText(DatasetFields.END_DATE)),
                        value -> values(DatasetFields.START_DATE, value.startDate(), DatasetFields.END_DATE, value.endDate())),
                new ParameterCodec<>(of(tsCode, tradeDate), TradeDateParameters.class,
                        json -> new TradeDateParameters(json.nullableText(DatasetFields.TS_CODE), json.nullableText(DatasetFields.TRADE_DATE)),
                        value -> values(DatasetFields.TS_CODE, value.tsCode(), DatasetFields.TRADE_DATE, value.tradeDate())),
                new ParameterCodec<>(of(tradeDate), TradeDateOnlyParameters.class,
                        json -> new TradeDateOnlyParameters(json.nullableText(DatasetFields.TRADE_DATE)),
                        value -> values(DatasetFields.TRADE_DATE, value.tradeDate())),
                new ParameterCodec<>(of(tsCode), TsCodeParameters.class,
                        json -> new TsCodeParameters(json.nullableText(DatasetFields.TS_CODE)),
                        value -> values(DatasetFields.TS_CODE, value.tsCode())),
                new ParameterCodec<>(of(tsCode, annDate), TsCodeAnnDateParameters.class,
                        json -> new TsCodeAnnDateParameters(json.nullableText(DatasetFields.TS_CODE), json.nullableText(DatasetFields.ANN_DATE)),
                        value -> values(DatasetFields.TS_CODE, value.tsCode(), DatasetFields.ANN_DATE, value.annDate())),
                new ParameterCodec<>(of(new ParameterShape.Field(SCENARIO, ENUM, true, SUCCESS_SCENARIO,
                        Set.of(SUCCESS_SCENARIO, "EMPTY", "SOURCE_FAILURE", "TYPE_FAILURE", "PERSISTENCE_FAILURE"),
                        null, null)), ScenarioParameters.class,
                        json -> new ScenarioParameters(json.nullableText(SCENARIO)),
                        value -> values(SCENARIO, value.scenario())));
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
