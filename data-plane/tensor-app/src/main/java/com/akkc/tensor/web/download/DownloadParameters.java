package com.akkc.tensor.web.download;

/** The supported download parameter structures at the HTTP boundary. */
public sealed interface DownloadParameters {
    record SnapshotParameters() implements DownloadParameters {}
    record AnnDateParameters(String annDate) implements DownloadParameters {}
    record ExchangeParameters(String exchange) implements DownloadParameters {}
    record ExchangeDateRangeParameters(String exchange, String startDate, String endDate)
            implements DownloadParameters {}
    record TsCodeDateRangeParameters(String tsCode, String startDate, String endDate)
            implements DownloadParameters {}
    record ExchangeIdDateRangeParameters(String exchangeId, String startDate, String endDate)
            implements DownloadParameters {}
    record ExchangeTradeDateParameters(String exchangeId, String tradeDate) implements DownloadParameters {}
    record HsTypeParameters(String hsType) implements DownloadParameters {}
    record ListStatusParameters(String listStatus) implements DownloadParameters {}
    record MonthParameters(String month) implements DownloadParameters {}
    record DateRangeParameters(String startDate, String endDate) implements DownloadParameters {}
    record TradeDateParameters(String tradeDate) implements DownloadParameters {}
    record TsCodeParameters(String tsCode) implements DownloadParameters {}
    record TsCodeAnnDateParameters(String tsCode, String annDate) implements DownloadParameters {}
    record ScenarioDateRangeParameters(String scenario, String startDate, String endDate) implements DownloadParameters {}
    record ScenarioStockDateRangeParameters(String scenario, String tsCode, String startDate, String endDate) implements DownloadParameters {}
    record ScenarioParameters(String scenario) implements DownloadParameters {}
}
