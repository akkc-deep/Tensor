package com.akkc.tensor.plugin.api;

import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import java.util.Objects;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.util.Map;

public interface DataSourcePlugin {
    PluginDescriptor descriptor();

    PluginReadiness readiness();

    FetchResult download(ApiName apiName, Map<String, Object> sourceParams, DownloadContext context);

    default DownloadPolicy.BatchPlanning planBatch(ApiName apiName, FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(context, "context").checkServerState();
        throw new SourceException(
                ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, "Source completeness is unconfirmed");
    }

    default FetchResult fetchBatch(ApiName apiName, FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(context, "context").checkServerState();
        throw new SourceException(
                ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, "Source completeness is unconfirmed");
    }

    default CalendarDecision confirmCalendar(ApiName apiName, CalendarScope scope, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(context, "context").checkServerState();
        throw new CalendarUnconfirmedException();
    }
}
