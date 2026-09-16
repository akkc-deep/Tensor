package com.akkc.tensor.plugin.tushare;

/** Tushare plugin identity, API names and exchange codes. */
public final class TushareConstants {
    public static final long DAILY_ROW_LIMIT = 6000L;
    public static final long WEEKLY_ROW_LIMIT = 6000L;
    public static final long MONTHLY_ROW_LIMIT = 4500L;
    public static final long DAILY_BASIC_ROW_LIMIT = 6000L;
    public static final long STK_LIMIT_ROW_LIMIT = 5800L;
    public static final long MONEYFLOW_ROW_LIMIT = 6000L;
    public static final long MARGIN_ROW_LIMIT = 4000L;
    public static final long MARGIN_DETAIL_ROW_LIMIT = 6000L;
    public static final long BLOCK_TRADE_ROW_LIMIT = 1000L;
    public static final long SLB_LEN_ROW_LIMIT = 5000L;
    public static final long SLB_SEC_ROW_LIMIT = 5000L;
    public static final long SLB_SEC_DETAIL_ROW_LIMIT = 5000L;
    public static final long NEW_SHARE_ROW_LIMIT = 2000L;
    public static final long FORECAST_ROW_LIMIT = 3500L;
    public static final long STK_HOLDERNUMBER_ROW_LIMIT = 3000L;
    public static final long STK_HOLDERTRADE_ROW_LIMIT = 3000L;
    public static final long PLEDGE_DETAIL_ROW_LIMIT = 1000L;
    public static final long FINA_INDICATOR_ROW_LIMIT = 100L;
    public static final long FINA_MAINBZ_ROW_LIMIT = 100L;
    public static final long TOP_LIST_ROW_LIMIT = 10000L;
    public static final long DIVIDEND_ROW_LIMIT = 2000L;
    public static final long DISCLOSURE_DATE_ROW_LIMIT = 6000L;

    public static final int API_COUNT = 40;

    public static final int MAX_READ_TIMEOUT_SECONDS = 120;
    public static final int MAX_RESPONSE_BYTES = 67_108_864;
    public static final int DEFAULT_MIN_REQUEST_INTERVAL_MILLIS = 1_500;
    public static final String PLUGIN_ID = "tushare_pro";
    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";
    public static final String ADJ_FACTOR = "adj_factor";
    public static final String SUSPEND_D = "suspend_d";
    public static final String TRADE_CAL = "trade_cal";
    public static final String NEW_SHARE = "new_share";
    public static final String INCOME = "income";
    public static final String BALANCESHEET = "balancesheet";
    public static final String CASHFLOW = "cashflow";
    public static final String FINA_AUDIT = "fina_audit";
    public static final String FORECAST = "forecast";
    public static final String EXPRESS = "express";
    public static final String REPURCHASE = "repurchase";
    public static final String STK_MANAGERS = "stk_managers";
    public static final String STK_HOLDERNUMBER = "stk_holdernumber";
    public static final String STK_HOLDERTRADE = "stk_holdertrade";
    public static final String PLEDGE_DETAIL = "pledge_detail";
    public static final String FINA_INDICATOR = "fina_indicator";
    public static final String FINA_MAINBZ = "fina_mainbz";
    public static final String TOP10_HOLDERS = "top10_holders";
    public static final String TOP10_FLOATHOLDERS = "top10_floatholders";
    public static final String TOP_LIST = "top_list";
    public static final String DIVIDEND = "dividend";
    public static final String DISCLOSURE_DATE = "disclosure_date";
    public static final String MARGIN = "margin";
    public static final String BLOCK_TRADE = "block_trade";
    public static final String SLB_LEN = "slb_len";
    public static final String SLB_SEC = "slb_sec";
    public static final String SLB_SEC_DETAIL = "slb_sec_detail";
    public static final String SSE = "SSE";
    public static final String SZSE = "SZSE";
    public static final String BSE = "BSE";

    private TushareConstants() {
    }
}
