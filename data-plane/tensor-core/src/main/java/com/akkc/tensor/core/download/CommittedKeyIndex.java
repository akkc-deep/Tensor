package com.akkc.tensor.core.download;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.core.adapter.BusinessContentCodec;
import com.akkc.tensor.core.persistence.BusinessKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class CommittedKeyIndex {
    private final DatasetKey datasetKey;
    private final Thread ownerThread;
    private final Map<BusinessKey, ContentDigest> values = new HashMap<>();

    public CommittedKeyIndex(DatasetKey datasetKey) {
        this.datasetKey = Objects.requireNonNull(datasetKey, "datasetKey");
        this.ownerThread = Thread.currentThread();
    }

    public void confirmCommitted(RecoveryUnitProcessor.ReadyUnit ready) {
        checkThread();
        Objects.requireNonNull(ready, "ready");
        if (ready.index() != this) throw new IllegalArgumentException("ready unit belongs to another committed index");
        if (ready.confirmed()) throw new IllegalStateException("ready unit was already confirmed");
        for (Map.Entry<BusinessKey, ContentDigest> entry : ready.pending().entrySet()) {
            ContentDigest digest = entry.getValue();
            if (digest.version() != BusinessContentCodec.VERSION) {
                throw new IllegalStateException("pending content version mismatch");
            }
            ContentDigest existing = values.get(entry.getKey());
            if (existing != null && !existing.equals(digest)) {
                throw new IllegalStateException("committed index changed after validation");
            }
        }
        values.putAll(ready.pending());
        ready.markConfirmed();
    }

    DatasetKey datasetKey() {
        checkThread();
        return datasetKey;
    }

    int size() {
        checkThread();
        return values.size();
    }

    ContentDigest lookup(BusinessKey key) {
        checkThread();
        return values.get(Objects.requireNonNull(key, "key"));
    }

    private void checkThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("committed index cannot be shared across threads");
        }
    }

    record ContentDigest(int version, String sha256Hex) {
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        ContentDigest {
            Objects.requireNonNull(sha256Hex, "sha256Hex");
            if (version < 1 || !SHA256.matcher(sha256Hex).matches()) {
                throw new IllegalArgumentException("invalid content digest");
            }
        }
    }
}
