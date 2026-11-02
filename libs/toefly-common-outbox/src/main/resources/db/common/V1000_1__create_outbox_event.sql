-- Transactional outbox read by Debezium (docs/04-architecture.md §6.3).
CREATE TABLE outbox_event (
    id              uuid         PRIMARY KEY,
    aggregate_type  varchar(64)  NOT NULL,
    aggregate_id    varchar(64)  NOT NULL,
    topic           varchar(200) NOT NULL,
    partition_key   varchar(64)  NOT NULL,
    event_type      varchar(200) NOT NULL,
    source          varchar(100) NOT NULL,
    subject         varchar(200),
    occurred_at     timestamptz  NOT NULL,
    data_schema     varchar(300) NOT NULL,
    payload         bytea        NOT NULL,
    trace_parent    varchar(55),
    correlation_id  uuid,
    causation_id    uuid,
    user_id         uuid,
    spec_version    varchar(10)  NOT NULL DEFAULT '1.0',           -- ce_specversion; an SMT literal would arrive as a number
    created_at      timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX outbox_event_created_at_idx ON outbox_event (created_at);
CREATE INDEX outbox_event_user_id_idx ON outbox_event (user_id) WHERE user_id IS NOT NULL;
