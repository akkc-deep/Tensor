package com.akkc.tensor.plugin.api.download.batch;

/** Only COMPLETE permits range data to proceed to adaptation and persistence. */
public enum BatchAssessment {
    COMPLETE, SPLIT_REQUIRED, UNKNOWN
}
