package com.toefly.common.outbox;

/** Stores outbox rows; the JDBC implementation is {@link JdbcOutboxWriter}. */
public interface OutboxWriter {

    void insert(OutboxRow row);
}
