package com.akkc.tensor.core.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;

public final class JdbcValueBinder {
    public void bind(PreparedStatement statement, int index, Object value, int jdbcType) throws SQLException {
        Objects.requireNonNull(statement, "statement");
        if (index < 1) {
            throw new IllegalArgumentException("index must be positive");
        }
        if (value == null) {
            statement.setNull(index, jdbcType);
        } else if (value instanceof String text) {
            statement.setString(index, text);
        } else if (value instanceof LocalDate date) {
            statement.setDate(index, Date.valueOf(date));
        } else if (value instanceof Long number) {
            statement.setLong(index, number);
        } else if (value instanceof BigDecimal decimal) {
            statement.setBigDecimal(index, decimal);
        } else if (value instanceof Instant instant) {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            statement.setTimestamp(index, Timestamp.from(instant), utc);
        } else {
            throw new IllegalArgumentException("Unsupported JDBC value type");
        }
    }
}
