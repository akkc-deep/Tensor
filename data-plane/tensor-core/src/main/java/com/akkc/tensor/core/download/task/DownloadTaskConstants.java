package com.akkc.tensor.core.download.task;

/** Defaults shared by task settings and application property binding. */
public final class DownloadTaskConstants {
    public static final int SPLIT_CHILD_COUNT = 2;
    public static final int MAX_BATCH_KEY_LENGTH = 128;

    public static final int DEFAULT_MAX_QUEUED_TASKS = 100;
    public static final int DEFAULT_MAX_RANGE_DAYS = 36_600;
    public static final int DEFAULT_MAX_BATCH_NODES = 10_000;
    public static final long DEFAULT_MAX_REQUESTS_PER_RUN = 5_000;
    public static final int DEFAULT_MAX_RUN_DURATION_MINUTES = 30;
    public static final long DEFAULT_MAX_SOURCE_ROWS_PER_TASK = 1_000_000;
    public static final String BATCH_KEY_FORMAT = "%06d";
    public static final String LEFT_BATCH_SUFFIX = "/0";
    public static final String RIGHT_BATCH_SUFFIX = "/1";

    private DownloadTaskConstants() {
    }
}
