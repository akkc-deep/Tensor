package com.akkc.tensor.core.persistence;

/** Shared SQL fragments and transaction timeout in seconds. */
public final class SqlConstants {
    public static final int TRANSACTION_TIMEOUT_SECONDS = 60;
    public static final String COLUMN_SEPARATOR = ", ";
    public static final String PARAMETER = "?";
    public static final String EQUALS_PARAMETER = "=?";
    public static final String SELECT = "SELECT ";
    public static final String FROM = " FROM ";
    public static final String WHERE = " WHERE ";
    public static final String AND = " AND ";
    public static final String FOR_UPDATE = " FOR UPDATE";
    public static final String ASCENDING = " ASC";
    public static final String CLOSE_PARENTHESIS = ")";
    public static final String IDENTIFIER_QUOTE = "`";
    public static final String VALUES_ASSIGNMENT = " = VALUES(";
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String COLUMN_LIST_START = " (";
    public static final String VALUES_START = ") VALUES (";
    public static final String DUPLICATE_KEY_UPDATE = ") ON DUPLICATE KEY UPDATE ";

    private SqlConstants() {
    }
}
