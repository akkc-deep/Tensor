package com.akkc.tensor.web.dto;

public record DownloadTaskControlRequest(long expectedVersion) {
    public DownloadTaskControlRequest {
        if (expectedVersion < 1) throw new IllegalArgumentException("Invalid task version");
    }
}
