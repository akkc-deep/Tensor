package com.akkc.tensor.plugin.api.constant;

/** Messages used by multiple validators and response mappers. */
public final class ValidationMessages {
    public static final String MUST_NOT_BE_BLANK = " must not be blank";
    public static final String NULL_BUSINESS_KEY = "business keys must not contain null";
    public static final String INVALID_SOURCE_FAILURE_CODE = "code must identify a source failure";
    public static final String INVALID_VALUE = "has invalid value";
    public static final String REQUIRED = "is required";
    public static final String COLUMNS_EMPTY = "columns must not be empty";
    public static final String DUPLICATE_COLUMNS = "columns must not contain duplicates";
    public static final String FIELDS_EMPTY = "fields must not be empty";
    public static final String DUPLICATE_FIELDS = "fields must not contain duplicates";
    public static final String DUPLICATE_ALLOWED_VALUES = "allowedValues must not contain duplicates";
    public static final String DUPLICATE_PARAMETERS = "parameters must not contain duplicate names";
    public static final String INVALID_FIELD_PREFIX = "Invalid field: ";
    public static final String CATEGORY_TOO_LONG = "category must be at most 64 characters";
    public static final String MESSAGE_BLANK = "message must not be blank";
    public static final String NEGATIVE_ROW_COUNTS = "row counts must be non-negative";
    public static final String TABLE_NAME_MISMATCH = "tableName must match datasetKey";
    public static final String QUERY_PARAMETERS_INVALID = "Query parameters are invalid";

    private ValidationMessages() {
    }
}
