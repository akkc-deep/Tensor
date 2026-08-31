package com.akkc.tensor.plugin.api;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.Instant;

public interface DatasetAdapter {
    DatasetKey datasetKey();

    DatasetDefinition definition();

    AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt);
}
