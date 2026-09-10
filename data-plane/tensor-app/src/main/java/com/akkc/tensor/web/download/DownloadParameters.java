package com.akkc.tensor.web.download;

/** The supported download parameter structures at the HTTP boundary. */
public sealed interface DownloadParameters {
    record SnapshotParameters() implements DownloadParameters {}
    record AnnDateParameters(String annDate) implements DownloadParameters {}
    record ExchangeParameters(String tsCode, String exchange) implements DownloadParameters {}
    record ExchangeDateRangeParameters(String exchange, String startDate, String endDate)
            implements DownloadParameters {}
    record ExchangeTradeDateParameters(String exchangeId, String tradeDate) implements DownloadParameters {}
    record ListStatusParameters(String tsCode, String listStatus) implements DownloadParameters {}
    record DateRangeParameters(String startDate, String endDate) implements DownloadParameters {}
    record TradeDateParameters(String tsCode, String tradeDate) implements DownloadParameters {}
    record TradeDateOnlyParameters(String tradeDate) implements DownloadParameters {}
    record TsCodeParameters(String tsCode) implements DownloadParameters {}
    record TsCodeAnnDateParameters(String tsCode, String annDate) implements DownloadParameters {}
    record ScenarioParameters(String scenario) implements DownloadParameters {}
}
