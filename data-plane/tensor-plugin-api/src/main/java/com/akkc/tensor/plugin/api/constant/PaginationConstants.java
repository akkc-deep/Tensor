package com.akkc.tensor.plugin.api.constant;

import java.util.Set;
import java.util.stream.Collectors;

/** Shared page sizes; dataset and task endpoints retain their own defaults. */
public final class PaginationConstants {
    public static final String INVALID_PAGE = "page must be at least 1";
    public static final String INVALID_PAGE_SIZE = "pageSize must be one of 20, 50, 100";
    public static final int FIRST_PAGE = 1;
    public static final int SMALL_PAGE_SIZE = 20;
    public static final int MEDIUM_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_TASK_PAGE_SIZE = SMALL_PAGE_SIZE;
    public static final int DEFAULT_DATASET_PAGE_SIZE = MEDIUM_PAGE_SIZE;
    public static final Set<Integer> PAGE_SIZES = Set.of(
            SMALL_PAGE_SIZE, MEDIUM_PAGE_SIZE, MAX_PAGE_SIZE);
    public static final String FIRST_PAGE_TEXT = "" + FIRST_PAGE;
    public static final String DEFAULT_TASK_PAGE_SIZE_TEXT = "" + DEFAULT_TASK_PAGE_SIZE;
    public static final Set<String> PAGE_SIZE_VALUES = PAGE_SIZES.stream()
            .map(String::valueOf).collect(Collectors.toUnmodifiableSet());

    private PaginationConstants() {
    }
}
