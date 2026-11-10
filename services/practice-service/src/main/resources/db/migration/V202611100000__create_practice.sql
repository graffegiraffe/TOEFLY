-- Event store and projections of practice-service (docs/04-architecture.md §6.6, docs/03-domain-model.md §6.5).

CREATE TABLE attempt_event (
    global_position bigserial    PRIMARY KEY,
    stream_id       uuid         NOT NULL,
    version         int          NOT NULL,
    event_type      varchar(100) NOT NULL,
    payload         jsonb        NOT NULL,
    metadata        jsonb        NOT NULL,
    occurred_at     timestamptz  NOT NULL,
    user_id         uuid         NOT NULL,
    CONSTRAINT attempt_event_stream_version_uk UNIQUE (stream_id, version)
);
CREATE INDEX attempt_event_user_idx ON attempt_event (user_id, occurred_at);

CREATE TABLE attempt_snapshot (
    stream_id      uuid        PRIMARY KEY,
    version        int         NOT NULL,
    state          jsonb       NOT NULL,
    schema_version int         NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE attempt_view (
    attempt_id       uuid         PRIMARY KEY,
    user_id          uuid         NOT NULL,
    item_set_id      uuid         NOT NULL,
    item_set_version int          NOT NULL,
    task_type        varchar(40)  NOT NULL,
    section          varchar(20),
    mode             varchar(10)  NOT NULL,
    status           varchar(20)  NOT NULL,
    week_task_id     uuid,
    exam_session_id  uuid,
    started_at       timestamptz  NOT NULL,
    submitted_at     timestamptz,
    scored_at        timestamptz,
    raw_score        numeric(7, 2),
    max_score        numeric(7, 2) NOT NULL,
    item_count       int          NOT NULL,
    correct_count    int          NOT NULL DEFAULT 0,
    unanswered_count int          NOT NULL DEFAULT 0,
    active_seconds   int          NOT NULL DEFAULT 0,
    estimated_seconds int         NOT NULL,
    answers          jsonb        NOT NULL DEFAULT '{}'::jsonb,
    review           jsonb,
    last_activity_at timestamptz  NOT NULL,
    version          int          NOT NULL
);
CREATE INDEX attempt_view_user_idx ON attempt_view (user_id, started_at DESC, attempt_id DESC);
CREATE UNIQUE INDEX attempt_view_open_practice_uk ON attempt_view (user_id, item_set_id)
    WHERE status = 'IN_PROGRESS' AND mode = 'PRACTICE';
CREATE INDEX attempt_view_stale_idx ON attempt_view (mode, last_activity_at) WHERE status = 'IN_PROGRESS';

-- Item set versions pinned by attempts, with keys; the keys never leave practice_db (ADR-0010).
CREATE TABLE item_set_snapshot (
    item_set_id uuid        NOT NULL,
    version     int         NOT NULL,
    payload     jsonb       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (item_set_id, version)
);

-- Published sets from item-set-published / item-set-hidden (INV-ATT-06).
CREATE TABLE item_pool (
    item_set_id uuid        PRIMARY KEY,
    version     int         NOT NULL,
    exam_format varchar(12) NOT NULL,
    available   boolean     NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE item_difficulty (
    item_id     uuid         PRIMARY KEY,
    item_set_id uuid         NOT NULL REFERENCES item_pool (item_set_id) ON DELETE CASCADE,
    ordinal     smallint     NOT NULL,
    b           numeric(5, 3),
    basis       varchar(12)  NOT NULL DEFAULT 'SEED_LABEL',
    UNIQUE (item_set_id, ordinal)
);

CREATE TABLE ability_estimate (
    user_id       uuid          NOT NULL,
    section       varchar(20)   NOT NULL,
    theta         numeric(5, 3) NOT NULL,
    info          numeric(8, 3) NOT NULL,
    n             int           NOT NULL,
    last_event_id uuid,
    version       int           NOT NULL,
    updated_at    timestamptz   NOT NULL,
    PRIMARY KEY (user_id, section)
);

CREATE TABLE ability_history (
    id             bigserial     PRIMARY KEY,
    user_id        uuid          NOT NULL,
    section        varchar(20)   NOT NULL,
    attempt_id     uuid          NOT NULL UNIQUE,
    theta_before   numeric(5, 3) NOT NULL,
    theta_after    numeric(5, 3) NOT NULL,
    standard_error numeric(5, 3) NOT NULL,
    items_count    smallint      NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ability_history_user_idx ON ability_history (user_id, section, created_at);

-- Responses kept until a section first reaches 8 items, for the MAP estimate (docs/03-domain-model.md §7.3).
CREATE TABLE ability_response (
    id         bigserial     PRIMARY KEY,
    user_id    uuid          NOT NULL,
    section    varchar(20)   NOT NULL,
    b          numeric(5, 3) NOT NULL,
    x          numeric(4, 3) NOT NULL,
    created_at timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ability_response_user_idx ON ability_response (user_id, section);

CREATE TABLE time_entry (
    id                uuid        PRIMARY KEY,
    user_id           uuid        NOT NULL,
    context_type      varchar(24) NOT NULL,
    context_id        uuid        NOT NULL,
    task_type         varchar(40),
    week_section      varchar(20),
    local_date        date        NOT NULL,
    started_at        timestamptz NOT NULL,
    estimated_seconds int         NOT NULL DEFAULT 0,
    active_seconds    int         NOT NULL DEFAULT 0,
    last_sequence     bigint      NOT NULL DEFAULT 0,
    last_heartbeat_at timestamptz,
    paused            boolean     NOT NULL DEFAULT false,
    audio_replays     int         NOT NULL DEFAULT 0,
    suspicious        boolean     NOT NULL DEFAULT false,
    closed_at         timestamptz,
    version           int         NOT NULL,
    UNIQUE (context_type, context_id, user_id)
);
CREATE INDEX time_entry_user_day_idx ON time_entry (user_id, local_date);

CREATE TABLE tutor_student_link (
    student_id uuid        NOT NULL,
    tutor_id   uuid        NOT NULL,
    linked_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, tutor_id)
);
