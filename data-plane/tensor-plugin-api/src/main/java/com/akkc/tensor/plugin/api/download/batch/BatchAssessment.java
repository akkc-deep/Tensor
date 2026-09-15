package com.akkc.tensor.plugin.api.download.batch;

/** COMPLETE and RESPONSE_ONLY permit persistence only under their matching policy rules. */
public enum BatchAssessment {
    COMPLETE, SPLIT_REQUIRED, RESPONSE_ONLY, UNKNOWN
}
