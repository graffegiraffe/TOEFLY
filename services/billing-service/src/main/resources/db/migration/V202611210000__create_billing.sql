-- billing_db (docs/03-domain-model.md §6.13, docs/04-architecture.md §3.14).

CREATE TABLE plan_tier (
    code            varchar(12)  PRIMARY KEY,
    audience        varchar(8)   NOT NULL CHECK (audience IN ('STUDENT', 'TUTOR')),
    price_minor     int          NOT NULL CHECK (price_minor >= 0),
    currency        char(3)      NOT NULL,
    billing_interval varchar(8)  NOT NULL DEFAULT 'MONTH',
    seat_limit      smallint,
    storage_bytes   bigint,
    trial_days      smallint     NOT NULL DEFAULT 0,
    free_ai_check_limit smallint,
    entitlements    text[]       NOT NULL,
    sort_order      smallint     NOT NULL
);

-- Prices and rights: docs/01 §6.1, docs/02 §1.3. The Free limit of 10 AI checks follows docs/02 (docs/03 says 5).
INSERT INTO plan_tier (code, audience, price_minor, currency, seat_limit, storage_bytes, trial_days,
                       free_ai_check_limit, entitlements, sort_order) VALUES
    ('STUDENT_FREE', 'STUDENT', 0, 'EUR', NULL, NULL, 0, 10,
        ARRAY['AI_WRITING_CHECK', 'AI_SPEAKING_CHECK', 'SPEAKING', 'WEEKLY_REVIEW'], 1),
    ('STUDENT_PRO', 'STUDENT', 1200, 'EUR', NULL, NULL, 7, NULL,
        ARRAY['UNLIMITED_WEEKS', 'AI_WRITING_CHECK', 'AI_SPEAKING_CHECK', 'SPEAKING', 'MOCK_EXAM', 'AI_TUTOR_CHAT',
              'WEEKLY_REVIEW'], 2),
    ('TUTOR', 'TUTOR', 2900, 'EUR', 10, 2147483648, 0, NULL, ARRAY['TUTOR_WORKSPACE', 'TUTOR_MATERIALS'], 3),
    ('TUTOR_PLUS', 'TUTOR', 5900, 'EUR', 30, 10737418240, 0, NULL, ARRAY['TUTOR_WORKSPACE', 'TUTOR_MATERIALS'], 4);

CREATE TABLE subscription (
    id                       uuid         PRIMARY KEY,
    user_id                  uuid         NOT NULL,
    plan_tier_code           varchar(12)  NOT NULL REFERENCES plan_tier (code),
    provider                 varchar(10),
    provider_subscription_id varchar(100),
    provider_customer_id     varchar(100),
    status                   varchar(10)  NOT NULL
        CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    current_period_start     timestamptz  NOT NULL,
    current_period_end       timestamptz  NOT NULL,
    trial_ends_at            timestamptz,
    trial_ending_notified    boolean      NOT NULL DEFAULT false,
    cancel_at_period_end     boolean      NOT NULL DEFAULT false,
    canceled_at              timestamptz,
    grace_until              timestamptz,
    pending_plan_tier_code   varchar(12)  REFERENCES plan_tier (code),
    test_mode                boolean      NOT NULL DEFAULT true,
    ended_at                 timestamptz,
    end_reason               varchar(20),
    created_at               timestamptz  NOT NULL,
    version                  int          NOT NULL DEFAULT 0
);
-- INV-BIL-01: at most one live subscription per user.
CREATE UNIQUE INDEX subscription_one_live_per_user ON subscription (user_id) WHERE status <> 'EXPIRED';
CREATE UNIQUE INDEX subscription_provider_ref ON subscription (provider, provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;
CREATE INDEX subscription_due_idx ON subscription (status, current_period_end);
CREATE INDEX subscription_created_idx ON subscription (created_at DESC, id DESC);

CREATE TABLE seat (
    id              uuid         PRIMARY KEY,
    subscription_id uuid         NOT NULL REFERENCES subscription (id),
    index_no        smallint     NOT NULL CHECK (index_no BETWEEN 1 AND 30),
    student_id      uuid,
    assigned_at     timestamptz,
    UNIQUE (subscription_id, index_no)
);
-- INV-BIL-02: a student holds at most one seat.
CREATE UNIQUE INDEX seat_student_uk ON seat (student_id) WHERE student_id IS NOT NULL;

CREATE TABLE entitlement_grant (
    id           uuid         PRIMARY KEY,
    user_id      uuid         NOT NULL,
    entitlement  varchar(30)  NOT NULL,
    source       varchar(12)  NOT NULL CHECK (source IN ('SUBSCRIPTION', 'TRIAL', 'SEAT', 'MANUAL')),
    source_id    uuid,
    grant_limit  int,
    used         int,
    valid_until  timestamptz
);
CREATE INDEX entitlement_grant_user_idx ON entitlement_grant (user_id);

-- The last published snapshot of each user; its version grows with every change (INV-BIL-04).
CREATE TABLE entitlement_snapshot (
    user_id        uuid         PRIMARY KEY,
    version        bigint       NOT NULL,
    plan_tier_code varchar(12)  NOT NULL,
    entitlements   text[]       NOT NULL,
    fingerprint    char(64)     NOT NULL,
    tutor_id       uuid,
    trial_ends_at  timestamptz,
    changed_at     timestamptz  NOT NULL
);

-- Lifetime counter of AI checks on STUDENT_FREE (docs/02 §1.3, rule 1).
CREATE TABLE free_ai_usage (
    user_id    uuid        PRIMARY KEY,
    used       int         NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL
);

-- AC-27.02.4: irreversible hashes of identities that already had a trial, kept 12 months.
CREATE TABLE trial_registry (
    identity_hash char(64)    PRIMARY KEY,
    granted_at    timestamptz NOT NULL
);

CREATE TABLE checkout_session (
    id                  uuid         PRIMARY KEY,
    user_id             uuid         NOT NULL,
    idempotency_key     varchar(100) NOT NULL,
    plan_tier_code      varchar(12)  NOT NULL REFERENCES plan_tier (code),
    provider            varchar(10)  NOT NULL,
    provider_session_id varchar(200),
    redirect_url        varchar(2000),
    return_url          varchar(500) NOT NULL,
    status              varchar(12)  NOT NULL CHECK (status IN ('OPEN', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    test_mode           boolean      NOT NULL,
    failure_reason      varchar(100),
    failure_message     varchar(300),
    saga_id             uuid,
    created_at          timestamptz  NOT NULL,
    expires_at          timestamptz  NOT NULL,
    UNIQUE (user_id, idempotency_key)
);
CREATE UNIQUE INDEX checkout_session_provider_ref ON checkout_session (provider, provider_session_id)
    WHERE provider_session_id IS NOT NULL;

CREATE TABLE payment (
    id                  uuid         PRIMARY KEY,
    subscription_id     uuid         REFERENCES subscription (id),
    user_id             uuid,
    plan_tier_code      varchar(12)  NOT NULL REFERENCES plan_tier (code),
    provider            varchar(10)  NOT NULL,
    provider_payment_id varchar(100) NOT NULL,
    amount_minor        int,
    currency            char(3),
    status              varchar(10)  NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    paid_at             timestamptz,
    failure_reason      varchar(200),
    receipt_url         varchar(500),
    test_mode           boolean      NOT NULL,
    created_at          timestamptz  NOT NULL,
    UNIQUE (provider, provider_payment_id)
);
CREATE INDEX payment_user_idx ON payment (user_id, created_at DESC);

-- INV-BIL-03: a webhook is stored once per provider event id; 200 only after this insert.
CREATE TABLE webhook_event (
    provider          varchar(10)  NOT NULL,
    provider_event_id varchar(100) NOT NULL,
    type              varchar(60)  NOT NULL,
    payload           jsonb        NOT NULL,
    received_at       timestamptz  NOT NULL,
    processed_at      timestamptz,
    attempts          smallint     NOT NULL DEFAULT 0,
    last_error        text,
    PRIMARY KEY (provider, provider_event_id)
);
CREATE INDEX webhook_event_pending_idx ON webhook_event (received_at) WHERE processed_at IS NULL;

CREATE TABLE saga_instance (
    id             uuid         PRIMARY KEY,
    saga_type      varchar(40)  NOT NULL,
    correlation_id uuid         NOT NULL UNIQUE,
    status         varchar(14)  NOT NULL CHECK (status IN ('RUNNING', 'COMPENSATING', 'COMPLETED', 'FAILED')),
    current_step   smallint     NOT NULL,
    user_id        uuid         NOT NULL,
    plan_tier_code varchar(12)  NOT NULL,
    provider       varchar(10)  NOT NULL,
    data           jsonb        NOT NULL,
    next_run_at    timestamptz  NOT NULL,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL
);
CREATE INDEX saga_instance_due_idx ON saga_instance (next_run_at) WHERE status IN ('RUNNING', 'COMPENSATING');
CREATE INDEX saga_instance_updated_idx ON saga_instance (updated_at DESC, id DESC);

CREATE TABLE saga_step (
    saga_id         uuid         NOT NULL REFERENCES saga_instance (id),
    step_no         smallint     NOT NULL,
    name            varchar(40)  NOT NULL,
    status          varchar(14)  NOT NULL CHECK (status IN ('PENDING', 'DONE', 'FAILED', 'SKIPPED', 'COMPENSATED')),
    attempts        smallint     NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_error      text,
    PRIMARY KEY (saga_id, step_no)
);
