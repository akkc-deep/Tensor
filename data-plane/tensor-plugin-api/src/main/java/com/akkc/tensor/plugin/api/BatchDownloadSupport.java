package com.akkc.tensor.plugin.api;

import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.batch.BatchAssessment;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Optional range capability; existing plugins retain their single-download contract. */
public interface BatchDownloadSupport extends DataSourcePlugin {
    /** Local metadata lookup, without an upstream request. Empty means unsupported. */
    Optional<BatchDownloadDescriptor> batchDescriptor(ApiName apiName);

    /**
     * Called only by a worker; may request upstream calendar data through the context.
     * Returns sorted, unique ranges inside the requested range. Native planning covers
     * the entire range, calendar planning covers every day, and trading-day planning
     * requires the plugin to verify complete calendar coverage before selecting open days.
     */
    List<DateRange> plan(ApiName apiName, Map<String, Object> params, BatchCallContext context);

    /** Pure conversion to persistable business parameters, preserving scope and excluding credentials. */
    Map<String, Object> sourceParameters(ApiName apiName, Map<String, Object> params, DateRange range);

    /** Returns an envelope whose source, API and parameters match the batch snapshot. */
    DownloadEnvelope downloadBatch(ApiName apiName, Map<String, Object> sourceParams, BatchCallContext context);

    /**
     * Before adaptation, verifies fields and source-specific stock/date scope, then
     * assesses completeness. Scope violations throw a classified source exception;
     * unknown completeness must remain UNKNOWN, including empty responses without evidence.
     */
    BatchAssessment assess(ApiName apiName, DateRange range, DownloadEnvelope envelope);
}
