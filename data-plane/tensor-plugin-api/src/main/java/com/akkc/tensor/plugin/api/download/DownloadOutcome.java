package com.akkc.tensor.plugin.api.download;

public enum DownloadOutcome {
    SUCCESS("下载成功"),
    EMPTY("下载成功，0 条数据");

    private final String message;

    DownloadOutcome(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
