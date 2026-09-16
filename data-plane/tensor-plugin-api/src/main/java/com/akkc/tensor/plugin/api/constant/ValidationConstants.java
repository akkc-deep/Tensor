package com.akkc.tensor.plugin.api.constant;

/** Shared lexical rules; date parsing retains each caller's resolver style. */
public final class ValidationConstants {
    public static final int MAX_DISPLAY_NAME_LENGTH = 128;

    public static final String IDENTIFIER_REGEX = "^[a-z][a-z0-9_]{1,63}$";
    public static final String TS_CODE_REGEX = "[A-Z0-9]+\\.[A-Z0-9]+";
    public static final String DATE_REGEX = "[0-9]{8}";
    public static final String MONTH_REGEX = "[0-9]{6}";
    public static final String UUID_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public static final String DATE_FORMAT = "uuuuMMdd";
    public static final String MONTH_FORMAT = "uuuuMM";
    public static final String FINGERPRINT_REGEX = "^[0-9a-f]{64}$";
    public static final int MAX_CATEGORY_LENGTH = 64;

    private ValidationConstants() {
    }
}
