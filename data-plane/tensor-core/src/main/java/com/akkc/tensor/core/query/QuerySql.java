package com.akkc.tensor.core.query;

import java.util.List;
import java.util.Objects;

public record QuerySql(String countSql, List<Object> countValues, String pageSql, List<Object> pageValues) {
    public QuerySql {
        Objects.requireNonNull(countSql, "countSql");
        countValues = List.copyOf(Objects.requireNonNull(countValues, "countValues"));
        Objects.requireNonNull(pageSql, "pageSql");
        pageValues = List.copyOf(Objects.requireNonNull(pageValues, "pageValues"));
    }
}
