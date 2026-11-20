-- Read models of analytics-service (docs/03 §6.15, docs/04 §6.7). The service owns no business invariants: every
-- table below is derived from event_journal and can be rebuilt from it.

-- Every consumed event, decoded, kept 400 days for rebuilds; monthly partitions are created by the maintenance job.
CREATE TABLE event_journal (
    event_id    uuid         NOT NULL,
    type        varchar(200) NOT NULL,
    user_id     uuid,
    occurred_at timestamptz  NOT NULL,
    recorded_at timestamptz  NOT NULL DEFAULT now(),
    payload     jsonb        NOT NULL,
    PRIMARY KEY (event_id, occurred_at)
) PARTITION BY RANGE (occurred_at);
CREATE TABLE event_journal_default PARTITION OF event_journal DEFAULT;
CREATE INDEX event_journal_replay_idx ON event_journal (occurred_at, event_id);
CREATE INDEX event_journal_user_idx ON event_journal (user_id);

-- Settings the projections depend on: day boundaries, daily goal, exam goal.
CREATE TABLE learner (
    user_id         uuid PRIMARY KEY,
    timezone        varchar(50)  NOT NULL,
    minutes_per_day smallint     NOT NULL,
    schedule_as_of  timestamptz,
    exam_date       date,
    overall_target  numeric(2, 1),
    section_targets jsonb        NOT NULL DEFAULT '{}',
    goal_as_of      timestamptz,
    plan_start      date
);

CREATE TABLE student_dashboard_view (
    user_id      uuid PRIMARY KEY,
    current_week jsonb,
    band_estimate jsonb,
    theta        jsonb       NOT NULL DEFAULT '{}',
    last_results jsonb       NOT NULL DEFAULT '[]',
    as_of        timestamptz
);

-- Weeks and band points behind the charts of Progress (AC-13.02.1).
CREATE TABLE week_summary (
    week_id          uuid PRIMARY KEY,
    user_id          uuid          NOT NULL,
    week_no          int           NOT NULL,
    day1             date,
    end_date         date,
    progress_percent numeric(5, 2) NOT NULL,
    tasks_total      int           NOT NULL,
    tasks_done       int           NOT NULL,
    completed        boolean       NOT NULL,
    closed_by        varchar(20),
    as_of            timestamptz   NOT NULL
);
CREATE INDEX week_summary_user_idx ON week_summary (user_id, week_no);

CREATE TABLE band_point (
    point_id      uuid PRIMARY KEY,
    user_id       uuid        NOT NULL,
    occurred_at   timestamptz NOT NULL,
    overall_point numeric(2, 1),
    overall_low   numeric(2, 1),
    overall_high  numeric(2, 1),
    sections      jsonb       NOT NULL
);
CREATE INDEX band_point_user_idx ON band_point (user_id, occurred_at);

CREATE TABLE activity_day (
    user_id              uuid     NOT NULL,
    local_date           date     NOT NULL,
    seconds_by_section   jsonb    NOT NULL,
    seconds_by_task_type jsonb    NOT NULL,
    pace_by_task_type    jsonb    NOT NULL,
    tasks_done           smallint NOT NULL,
    goal_minutes         smallint NOT NULL,
    day_plan_done        boolean  NOT NULL,
    goal_met             boolean  NOT NULL,
    active_seconds       int      NOT NULL,
    last_active_at       timestamptz,
    PRIMARY KEY (user_id, local_date)
);
CREATE INDEX activity_day_date_idx ON activity_day (local_date);

CREATE TABLE streak_view (
    user_id   uuid PRIMARY KEY,
    done_days date[]      NOT NULL,
    as_of     timestamptz NOT NULL
);

-- What the activity is computed from: the ETS norm of each attempt and the planned day of each task.
CREATE TABLE attempt_norm (
    attempt_id   uuid PRIMARY KEY,
    user_id      uuid NOT NULL,
    norm_seconds int  NOT NULL
);
CREATE INDEX attempt_norm_user_idx ON attempt_norm (user_id);

CREATE TABLE planned_task (
    week_task_id uuid PRIMARY KEY,
    user_id      uuid NOT NULL,
    planned_date date NOT NULL,
    done_date    date
);
CREATE INDEX planned_task_day_idx ON planned_task (user_id, planned_date);

CREATE TABLE learner_milestone (
    user_id         uuid PRIMARY KEY,
    registered_on   date,
    onboarded_on    date,
    plan_created_on date,
    first_task_on   date
);

-- Daily product counters, without identifiers of learners.
CREATE TABLE product_metrics_daily (
    day                         date PRIMARY KEY,
    new_users                   int NOT NULL DEFAULT 0,
    onboarded                   int NOT NULL DEFAULT 0,
    plans_created               int NOT NULL DEFAULT 0,
    first_tasks                 int NOT NULL DEFAULT 0,
    attempts_submitted          int NOT NULL DEFAULT 0,
    weeks_completed             int NOT NULL DEFAULT 0,
    weeks_completed_by_progress int NOT NULL DEFAULT 0
);

CREATE TABLE ai_usage_daily (
    day            date         NOT NULL,
    provider       varchar(30)  NOT NULL,
    model          varchar(100) NOT NULL,
    prompt_key     varchar(100) NOT NULL,
    tokens_in      bigint       NOT NULL,
    tokens_out     bigint       NOT NULL,
    cost_micro_usd bigint       NOT NULL,
    calls          int          NOT NULL,
    fallback_calls int          NOT NULL,
    cache_hits     int          NOT NULL,
    PRIMARY KEY (day, provider, model, prompt_key)
);

CREATE TABLE projection_rebuild_job (
    id              uuid PRIMARY KEY,
    projection      varchar(40) NOT NULL,
    status          varchar(20) NOT NULL,
    requested_by    uuid        NOT NULL,
    requested_at    timestamptz NOT NULL,
    finished_at     timestamptz,
    events_replayed bigint      NOT NULL DEFAULT 0,
    error           varchar(200),
    idempotency_key uuid        NOT NULL,
    UNIQUE (requested_by, idempotency_key)
);
CREATE UNIQUE INDEX projection_rebuild_job_running_idx ON projection_rebuild_job (projection) WHERE status = 'RUNNING';
