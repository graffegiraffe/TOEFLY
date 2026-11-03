-- Responses of requests with Idempotency-Key, kept for 24 hours (docs/05-api-contracts.md §2.6).
CREATE TABLE idempotency_record (
    idempotency_key varchar(100) NOT NULL,
    user_id         uuid         NOT NULL,
    operation       varchar(100) NOT NULL,
    request_hash    char(64)     NOT NULL,
    status          varchar(20)  NOT NULL,
    response_status int,
    response_body   jsonb,
    resource_uri    varchar(300),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    expires_at      timestamptz  NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);
CREATE INDEX idempotency_record_expires_at_idx ON idempotency_record (expires_at);
