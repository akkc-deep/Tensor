package com.akkc.tensor.web.dto;

import java.util.List;

public record DownloadTaskPage<T>(int page, int pageSize, long total, List<T> items) {
    public DownloadTaskPage { items = List.copyOf(items); }
}
