package com.akkc.tensor.plugin.api.integrity;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Same-snapshot, bounded local access; intentionally exposes no SQL or source client. */
public interface IntegrityContext {
    void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows);
    IntegrityStatistics compare(IntegrityExpectedKeys expected);
}
