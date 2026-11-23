-- tutor-agent-service (docs/06 §4–§6, docs/03 §11): learner model and mistake memory, chat threads, weekly reviews,
-- come-back messages and the local projections the agent reads.

CREATE EXTENSION IF NOT EXISTS vector;

-- Learner model (docs/06 §6.1): one row per learner; statistics are recomputed on every change.
CREATE TABLE learner_model (
    user_id    uuid PRIMARY KEY,
    version    bigint      NOT NULL,
    payload    jsonb       NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE mistake_record (
    id          uuid PRIMARY KEY,
    user_id     uuid         NOT NULL,
    category    varchar(20)  NOT NULL,
    subcategory varchar(60)  NOT NULL,
    source_type varchar(12)  NOT NULL,
    source_id   uuid         NOT NULL,
    item_id     uuid,
    task_type   varchar(40),
    excerpt     varchar(300),
    correction  varchar(300),
    explanation varchar(400),
    severity    varchar(12)  NOT NULL,
    unit_refs   text[]       NOT NULL DEFAULT '{}',
    created_at  timestamptz  NOT NULL
);
CREATE INDEX mistake_record_user_created ON mistake_record (user_id, created_at DESC);
CREATE INDEX mistake_record_user_subcategory ON mistake_record (user_id, subcategory, created_at DESC);

-- A chance to make a mistake: a subcategory (closed item) or a whole category (evaluated writing), docs/06 §6.2.
CREATE TABLE mistake_opportunity (
    id          bigserial PRIMARY KEY,
    user_id     uuid        NOT NULL,
    code        varchar(60) NOT NULL,
    source_id   uuid        NOT NULL,
    item_id     uuid,
    occurred_at timestamptz NOT NULL
);
CREATE INDEX mistake_opportunity_user_time ON mistake_opportunity (user_id, occurred_at DESC);

-- Vector memory of mistakes (docs/06 §6.4): nomic-embed-text, 768 dimensions, cosine distance. The embedding is filled
-- by a job after the record is stored, so an unavailable ai-gateway never blocks the event consumers.
CREATE TABLE mistake_memory (
    id          uuid PRIMARY KEY REFERENCES mistake_record (id) ON DELETE CASCADE,
    user_id     uuid         NOT NULL,
    subcategory varchar(60)  NOT NULL,
    content     varchar(400) NOT NULL,
    embedding   vector(768),
    attempts    int          NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL
);
CREATE INDEX mistake_memory_user ON mistake_memory (user_id);
CREATE INDEX mistake_memory_pending ON mistake_memory (created_at) WHERE embedding IS NULL;
CREATE INDEX mistake_memory_embedding ON mistake_memory USING hnsw (embedding vector_cosine_ops);

CREATE TABLE learner_model_snapshot (
    user_id    uuid        NOT NULL,
    week_no    int         NOT NULL,
    payload    jsonb       NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, week_no)
);

-- Debounce of learner-model-updated: the first change opens a 5-minute window, a job publishes when it ends.
CREATE TABLE learner_model_publication (
    user_id uuid PRIMARY KEY,
    due_at  timestamptz NOT NULL
);

-- Chat (docs/06 §4.6): the whole history of a thread; the model sees a window of it and the summary.
CREATE TABLE chat_thread (
    id              uuid PRIMARY KEY,
    user_id         uuid         NOT NULL,
    title           varchar(120) NOT NULL,
    context_type    varchar(24),
    context_ref     uuid,
    context_item_id uuid,
    context_label   varchar(120),
    language        varchar(2)   NOT NULL,
    summary         text,
    summarized_upto timestamptz,
    message_count   int          NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL,
    last_message_at timestamptz
);
CREATE INDEX chat_thread_user_last ON chat_thread (user_id, last_message_at DESC NULLS LAST, id);

CREATE TABLE chat_message (
    id              uuid PRIMARY KEY,
    thread_id       uuid         NOT NULL REFERENCES chat_thread (id) ON DELETE CASCADE,
    user_id         uuid         NOT NULL,
    role            varchar(10)  NOT NULL,
    text            text         NOT NULL,
    status          varchar(12)  NOT NULL,
    intent          varchar(24),
    context_type    varchar(24),
    context_ref     uuid,
    context_item_id uuid,
    context_label   varchar(120),
    tools           text[]       NOT NULL DEFAULT '{}',
    ai_stamp        jsonb,
    tokens_in       int,
    tokens_out      int,
    created_at      timestamptz  NOT NULL
);
CREATE INDEX chat_message_thread_created ON chat_message (thread_id, created_at, id);
CREATE INDEX chat_message_user_created ON chat_message (user_id, created_at DESC) WHERE role = 'USER';

-- Events of a streamed answer, kept for resuming with Last-Event-ID (AC-17.01.4); removed after a day.
CREATE TABLE chat_stream_event (
    message_id uuid        NOT NULL REFERENCES chat_message (id) ON DELETE CASCADE,
    seq        int         NOT NULL,
    event      varchar(12) NOT NULL,
    data       text        NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (message_id, seq)
);

CREATE TABLE pending_action (
    id         uuid PRIMARY KEY,
    user_id    uuid         NOT NULL,
    thread_id  uuid         NOT NULL REFERENCES chat_thread (id) ON DELETE CASCADE,
    message_id uuid         NOT NULL,
    type       varchar(24)  NOT NULL,
    title      varchar(120) NOT NULL,
    payload    jsonb        NOT NULL,
    status     varchar(12)  NOT NULL,
    result     jsonb,
    created_at timestamptz  NOT NULL,
    expires_at timestamptz  NOT NULL
);
CREATE INDEX pending_action_message ON pending_action (message_id);

CREATE TABLE tool_invocation (
    id            bigserial PRIMARY KEY,
    thread_id     uuid        NOT NULL REFERENCES chat_thread (id) ON DELETE CASCADE,
    message_id    uuid        NOT NULL,
    tool          varchar(40) NOT NULL,
    args_json     jsonb       NOT NULL,
    result_status varchar(20) NOT NULL,
    latency_ms    int         NOT NULL,
    created_at    timestamptz NOT NULL
);

-- Weekly reviews (docs/06 §5.3): at most one per week; a regeneration overwrites it (INV-AGT-03).
CREATE TABLE weekly_review (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL,
    week_id      uuid        NOT NULL UNIQUE,
    week_no      int         NOT NULL,
    language     varchar(2)  NOT NULL,
    ai_fallback  boolean     NOT NULL,
    low_activity boolean     NOT NULL,
    payload      jsonb       NOT NULL,
    tutor_note   text,
    generated_at timestamptz NOT NULL
);
CREATE INDEX weekly_review_user_week ON weekly_review (user_id, week_no DESC);

-- A review that waits for AI (docs/12, phase 5c, decision 8): every 10 minutes, up to 12 attempts, then the template.
CREATE TABLE deferred_review (
    week_id         uuid PRIMARY KEY,
    user_id         uuid        NOT NULL,
    request         jsonb       NOT NULL,
    attempts        int         NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    last_error      varchar(300),
    created_at      timestamptz NOT NULL
);

-- Projections the agent reads instead of synchronous calls.
CREATE TABLE entitlement_view (
    user_id    uuid PRIMARY KEY,
    features   text[]      NOT NULL,
    version    bigint      NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE active_exam_session (
    attempt_id uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    started_at timestamptz NOT NULL
);
CREATE INDEX active_exam_session_user ON active_exam_session (user_id);

CREATE TABLE learner_activity (
    user_id        uuid PRIMARY KEY,
    last_active_at timestamptz NOT NULL,
    timezone       varchar(64),
    first_name     varchar(80),
    exam_date      date,
    deleted        boolean     NOT NULL DEFAULT false
);

CREATE TABLE week_progress_view (
    user_id         uuid PRIMARY KEY,
    week_id         uuid        NOT NULL,
    week_no         int         NOT NULL,
    progress_pct    int         NOT NULL,
    rebalanced_at   timestamptz,
    updated_at      timestamptz NOT NULL
);

-- One come-back message per situation and episode (docs/06 §5.10).
CREATE TABLE reengagement_message (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL,
    situation   varchar(24) NOT NULL,
    episode_key varchar(40) NOT NULL,
    payload     jsonb       NOT NULL,
    created_at  timestamptz NOT NULL,
    UNIQUE (user_id, situation, episode_key)
);

-- Results of scored attempts and evaluated work by task type: the scores block of the weekly review.
CREATE TABLE practice_result (
    source_id   uuid PRIMARY KEY,
    user_id     uuid        NOT NULL,
    task_type   varchar(40) NOT NULL,
    accuracy    double precision,
    score       double precision,
    occurred_at timestamptz NOT NULL
);
CREATE INDEX practice_result_user_time ON practice_result (user_id, occurred_at DESC);
