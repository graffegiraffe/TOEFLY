package com.toefly.billing.adapter.out.persistence;

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

    static @Nullable Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static <E extends Enum<E>> @Nullable E enumOf(Class<E> type, @Nullable String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    static @Nullable String name(@Nullable Enum<?> value) {
        return value == null ? null : value.name();
    }
}
