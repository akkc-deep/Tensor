package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import java.util.Map;
import java.util.Set;

public interface TushareBatchSource {
    default DownloadPolicy.BatchPlanning plan(DatasetDefinition definition, ApiDescriptor api, FetchBatch batch) {
        throw TushareErrorClassifier.completenessUnconfirmed();
    }

    Session open(DatasetDefinition definition, ApiDescriptor api, FetchBatch batch);

    interface Session {
        Set<String> paginationParameters();

        Map<String, Object> pageParameters(String cursor);

        Observation observe(String cursor, DownloadEnvelope page);

        void validateComplete(DownloadEnvelope complete);
    }

    record Observation(End end, String nextCursor, Long totalRows) {
        public Observation {
            if (end == null
                    || end == End.CONTINUE && (nextCursor == null || nextCursor.isBlank())
                    || end != End.CONTINUE && nextCursor != null
                    || totalRows != null && totalRows < 0) {
                throw TushareErrorClassifier.invalidPayload();
            }
        }

        @Override
        public String toString() {
            return "Observation[REDACTED]";
        }
    }

    enum End { CONTINUE, COMPLETE, TRUNCATED, UNCONFIRMED }
}
