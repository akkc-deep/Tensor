package com.akkc.tensor.plugin.fixture;

/** Identity and deterministic values for the acceptance fixture. */
public final class FixtureConstants {
    public static final int AMOUNT_DISPLAY_ORDER = 2;
    public static final int NOTE_DISPLAY_ORDER = 3;
    public static final int NOTE_MAX_LENGTH = 255;
    public static final int TS_CODE_MAX_LENGTH = 64;

    public static final String PLUGIN_ID = "fixture";
    public static final String API_NAME = "fixture_daily";
    public static final String SCENARIO = "scenario";
    public static final String AMOUNT = "amount";
    public static final String NOTE = "note";
    public static final String SAMPLE_TS_CODE = "000001.SZ";
    public static final String SAMPLE_TRADE_DATE = "20260807";
    public static final String SAMPLE_AMOUNT = "11.23";

    private FixtureConstants() {
    }
}
