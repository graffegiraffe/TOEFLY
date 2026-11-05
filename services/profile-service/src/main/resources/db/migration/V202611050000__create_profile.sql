-- Profile aggregate of profile-service (docs/03-domain-model.md §6.2).
CREATE TABLE user_profile (
    user_id              uuid         PRIMARY KEY,
    email                varchar(320) NOT NULL,
    display_name         varchar(100),
    roles                varchar(60)  NOT NULL,
    timezone             varchar(64)  NOT NULL,
    explanation_language varchar(2)   NOT NULL,
    minutes_per_day      int          NOT NULL CHECK (minutes_per_day BETWEEN 15 AND 180),
    preferred_study_time time         NOT NULL,
    overall_target       numeric(2, 1),
    reading_target       numeric(2, 1),
    listening_target     numeric(2, 1),
    writing_target       numeric(2, 1),
    speaking_target      numeric(2, 1),
    exam_booked          boolean,
    exam_date            date,
    onboarding_status    varchar(20)  NOT NULL,
    onboarding_steps     varchar(100) NOT NULL DEFAULT '',
    goal_revision        int          NOT NULL DEFAULT 0,
    version              bigint       NOT NULL,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL
);

-- Goal history: one row per revision of onboarding-completed.
CREATE TABLE study_goal (
    user_id          uuid          NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    revision         int           NOT NULL,
    overall_target   numeric(2, 1) NOT NULL,
    reading_target   numeric(2, 1),
    listening_target numeric(2, 1),
    writing_target   numeric(2, 1),
    speaking_target  numeric(2, 1),
    exam_booked      boolean       NOT NULL,
    exam_date        date,
    minutes_per_day  int           NOT NULL,
    effective_from   timestamptz   NOT NULL,
    PRIMARY KEY (user_id, revision)
);

-- Consent journal: rows are never updated (INV-PRF-06).
CREATE TABLE consent (
    id             uuid        PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    consent_type   varchar(30) NOT NULL,
    granted        boolean     NOT NULL,
    policy_version varchar(20) NOT NULL,
    source         varchar(20) NOT NULL,
    ip_prefix      varchar(64),
    recorded_at    timestamptz NOT NULL
);
CREATE INDEX consent_user_type_idx ON consent (user_id, consent_type, recorded_at DESC);

CREATE TABLE data_export (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    status       varchar(20) NOT NULL,
    requested_at timestamptz NOT NULL,
    ready_at     timestamptz
);
CREATE INDEX data_export_user_idx ON data_export (user_id, requested_at DESC);

CREATE TABLE account_deletion (
    id                uuid        PRIMARY KEY,
    user_id           uuid        NOT NULL,
    status            varchar(20) NOT NULL,
    requested_at      timestamptz NOT NULL,
    legal_deadline_at timestamptz NOT NULL,
    completed_at      timestamptz
);
-- At most one deletion in progress per user (INV-PRF-07).
CREATE UNIQUE INDEX account_deletion_active_idx ON account_deletion (user_id) WHERE status <> 'COMPLETED';
