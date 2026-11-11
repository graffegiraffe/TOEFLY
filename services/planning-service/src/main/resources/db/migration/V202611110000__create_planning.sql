-- Plans, weeks and their tasks, and the local copies planning keeps of other services (docs/03-domain-model.md §6.8).

CREATE TABLE study_plan (
    id              uuid        PRIMARY KEY,
    user_id         uuid        NOT NULL,
    exam_date       date,
    weeks_total     smallint    NOT NULL CHECK (weeks_total >= 1),
    start_date      date        NOT NULL,
    timezone        varchar(50) NOT NULL,
    minutes_per_day smallint    NOT NULL CHECK (minutes_per_day > 0),
    current_week_no smallint    NOT NULL,
    targets         jsonb       NOT NULL DEFAULT '{}',
    goal_revision   int         NOT NULL,
    created_at      timestamptz NOT NULL,
    closed_at       timestamptz,
    version         int         NOT NULL,
    CONSTRAINT study_plan_user_uk UNIQUE (user_id)
);

CREATE TABLE week (
    id                uuid        PRIMARY KEY,
    plan_id           uuid        NOT NULL REFERENCES study_plan (id) ON DELETE CASCADE,
    user_id           uuid        NOT NULL,
    week_no           smallint    NOT NULL,
    status            varchar(26) NOT NULL,
    day1              date        NOT NULL,
    minutes_per_day   smallint    NOT NULL,
    mock_exam         boolean     NOT NULL,
    weak_types        jsonb       NOT NULL DEFAULT '[]',
    rationale         text,
    generated_at      timestamptz NOT NULL,
    published_at      timestamptz,
    completed_at      timestamptz,
    closed_on         date,
    closed_by         varchar(12),
    rebalanced_at     timestamptz,
    rebalance_count   smallint    NOT NULL DEFAULT 0,
    rebalance_skipped jsonb       NOT NULL DEFAULT '[]',
    last_rollover_date date,
    progress_percent  smallint    NOT NULL DEFAULT 0,
    version           int         NOT NULL,
    CONSTRAINT week_plan_no_uk UNIQUE (plan_id, week_no)
);
CREATE INDEX week_open_idx ON week (plan_id) WHERE status IN ('PUBLISHED', 'IN_PROGRESS');
CREATE INDEX week_user_idx ON week (user_id);

-- One row per day of the week (7, up to 10 while it is extended); tasks point at their day by day_no.
CREATE TABLE day_plan (
    week_id         uuid     NOT NULL REFERENCES week (id) ON DELETE CASCADE,
    day_no          smallint NOT NULL CHECK (day_no BETWEEN 1 AND 10),
    local_date      date     NOT NULL,
    planned_minutes smallint NOT NULL,
    PRIMARY KEY (week_id, day_no)
);

CREATE TABLE week_task (
    id                uuid         PRIMARY KEY,
    week_id           uuid         NOT NULL REFERENCES week (id) ON DELETE CASCADE,
    user_id           uuid         NOT NULL,
    day_no            smallint     NOT NULL CHECK (day_no BETWEEN 1 AND 10),
    kind              varchar(24)  NOT NULL,
    week_section      varchar(14)  NOT NULL,
    task_type         varchar(40),
    ref_id            varchar(500),
    title             varchar(200) NOT NULL,
    estimated_minutes numeric(5, 1) NOT NULL,
    status            varchar(12)  NOT NULL,
    origin            varchar(8)   NOT NULL,
    rationale         text,
    is_repeat         boolean      NOT NULL,
    skipped_reason    varchar(16),
    sort_order        smallint     NOT NULL,
    attempt_id        uuid,
    started_at        timestamptz,
    completed_at      timestamptz,
    completed_on      date
);
CREATE INDEX week_task_week_idx ON week_task (week_id);
CREATE INDEX week_task_user_ref_idx ON week_task (user_id, ref_id);

-- Approved item sets from the events of content-service (docs/12, phase 4, decision 10).
CREATE TABLE item_set_catalog (
    item_set_id       uuid             PRIMARY KEY,
    version           int              NOT NULL,
    task_type         varchar(40)      NOT NULL,
    exam_format       varchar(20)      NOT NULL,
    difficulty_b      double precision NOT NULL,
    estimated_minutes numeric(6, 2)    NOT NULL,
    topic             varchar(300)     NOT NULL,
    owner_tutor_id    uuid,
    hidden            boolean          NOT NULL DEFAULT false,
    updated_at        timestamptz      NOT NULL
);
CREATE INDEX item_set_catalog_type_idx ON item_set_catalog (task_type) WHERE NOT hidden;

-- θ by section from ability-updated; the newer event wins.
CREATE TABLE learner_theta (
    user_id     uuid             NOT NULL,
    section     varchar(14)      NOT NULL,
    theta       double precision NOT NULL,
    occurred_at timestamptz      NOT NULL,
    PRIMARY KEY (user_id, section)
);

-- Closed results of attempt-scored: the accuracy of the last two weeks decides the weak types.
CREATE TABLE attempt_result (
    attempt_id    uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL,
    task_type     varchar(40) NOT NULL,
    correct_count int         NOT NULL,
    total_count   int         NOT NULL,
    scored_at     timestamptz NOT NULL
);
CREATE INDEX attempt_result_user_idx ON attempt_result (user_id, scored_at);
