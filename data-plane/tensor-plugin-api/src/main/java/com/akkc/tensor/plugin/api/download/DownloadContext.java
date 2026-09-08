package com.akkc.tensor.plugin.api.download;

@FunctionalInterface
public interface DownloadContext {
    void checkServerState();
}
