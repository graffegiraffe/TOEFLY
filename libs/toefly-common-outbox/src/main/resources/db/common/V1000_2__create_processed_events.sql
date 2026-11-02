-- Deduplication of consumed events: one row per ce_id and consumer (docs/04-architecture.md §6.4).
CREATE TABLE processed_events (
    event_id        uuid         NOT NULL,
    consumer        varchar(100) NOT NULL,
    topic           varchar(200) NOT NULL,
    partition_no    int          NOT NULL,
    offset_no       bigint       NOT NULL,
    user_id         uuid,
    processed_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
CREATE INDEX processed_events_processed_at_idx ON processed_events (processed_at);
