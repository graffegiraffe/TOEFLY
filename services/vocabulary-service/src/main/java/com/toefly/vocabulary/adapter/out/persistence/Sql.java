package com.toefly.vocabulary.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Small JDBC helpers for nullable columns. */
final class Sql {

    private Sql() {}

    static @Nullable Timestamp ts(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static Instant requiredInstant(ResultSet rs, String column) throws SQLException {
        Instant value = instant(rs, column);
        if (value == null) {
            throw new SQLException(column + " is null");
        }
        return value;
    }

    static @Nullable UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    static UUID requiredUuid(ResultSet rs, String column) throws SQLException {
        UUID value = uuid(rs, column);
        if (value == null) {
            throw new SQLException(column + " is null");
        }
        return value;
    }

    static double number(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? 0 : value.doubleValue();
    }

    /** Values of numeric(p, 3) columns are rounded to 3 decimals as stored. */
    static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(3, java.math.RoundingMode.HALF_UP);
    }
}
