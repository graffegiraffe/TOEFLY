-- assessment-service (docs/03-domain-model.md §5, §6.6, §8): submissions with versions, evaluations with inline
-- annotations, deferred evaluations, inputs and snapshots of the band estimate, tutor links.

CREATE TABLE submission (
    id                 uuid PRIMARY KEY,
    user_id            uuid        NOT NULL,
    item_set_id        uuid        NOT NULL,
    item_set_version   int         NOT NULL,
    task_type          varchar(40) NOT NULL,
    mode               varchar(10) NOT NULL,
    week_task_id       uuid,
    exam_session_id    uuid,
    status             varchar(24) NOT NULL,
    current_version_no smallint    NOT NULL,
    version            int         NOT NULL,
    created_at         timestamptz NOT NULL
);
CREATE INDEX submission_user_created ON submission (user_id, created_at DESC);
CREATE INDEX submission_week_task ON submission (week_task_id) WHERE week_task_id IS NOT NULL;

CREATE TABLE submission_version (
    id                  uuid PRIMARY KEY,
    submission_id       uuid        NOT NULL REFERENCES submission (id) ON DELETE CASCADE,
    version_no          smallint    NOT NULL CHECK (version_no BETWEEN 1 AND 3),
    status              varchar(24) NOT NULL,
    body                text        NOT NULL,
    word_count          int         NOT NULL,
    active_seconds      int         NOT NULL,
    overtime_seconds    int         NOT NULL,
    counts_for_estimate boolean     NOT NULL,
    submitted_at        timestamptz,
    UNIQUE (submission_id, version_no)
);

CREATE TABLE evaluation (
    id                 uuid PRIMARY KEY,
    submission_id      uuid         NOT NULL REFERENCES submission (id) ON DELETE CASCADE,
    version_no         smallint     NOT NULL,
    evaluator          varchar(8)   NOT NULL,
    tutor_id           uuid,
    rubric_id          varchar(40)  NOT NULL,
    final_score        numeric(2, 1) NOT NULL CHECK (final_score BETWEEN 0 AND 5),
    overall_score      smallint     NOT NULL,
    criteria           jsonb        NOT NULL,
    strengths          jsonb        NOT NULL,
    priorities         jsonb        NOT NULL,
    passes             jsonb        NOT NULL,
    agreement          varchar(24),
    needs_human_review boolean      NOT NULL,
    caps_applied       jsonb        NOT NULL,
    integrity_flags    jsonb        NOT NULL,
    prompt_key         varchar(120),
    prompt_version     int          NOT NULL,
    model_id           varchar(100),
    degraded           boolean      NOT NULL,
    created_at         timestamptz  NOT NULL,
    UNIQUE (submission_id, version_no, evaluator)
);

CREATE TABLE inline_annotation (
    id            uuid PRIMARY KEY,
    evaluation_id uuid         NOT NULL REFERENCES evaluation (id) ON DELETE CASCADE,
    ordinal       smallint     NOT NULL,
    range_start   int,
    range_end     int,
    anchor_quote  text         NOT NULL,
    category      varchar(20)  NOT NULL,
    subcategory   varchar(60)  NOT NULL,
    severity      varchar(8)   NOT NULL,
    message       text         NOT NULL,
    suggestion    text,
    grammar_topic varchar(20),
    unit_title    varchar(200),
    status        varchar(14)  NOT NULL,
    CHECK ((range_start IS NULL) = (range_end IS NULL)),
    CHECK (range_start IS NULL OR (range_start >= 0 AND range_start < range_end))
);
CREATE INDEX inline_annotation_evaluation ON inline_annotation (evaluation_id, ordinal);

CREATE TABLE deferred_evaluation (
    submission_id   uuid        NOT NULL REFERENCES submission (id) ON DELETE CASCADE,
    version_no      smallint    NOT NULL,
    user_id         uuid        NOT NULL,
    attempts        smallint    NOT NULL,
    deferred_at     timestamptz NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    last_error      varchar(500),
    dead_lettered   boolean     NOT NULL DEFAULT false,
    PRIMARY KEY (submission_id, version_no)
);
CREATE INDEX deferred_evaluation_due ON deferred_evaluation (next_attempt_at);

CREATE TABLE ability_snapshot (
    user_id        uuid        NOT NULL,
    section        varchar(10) NOT NULL,
    theta          double precision NOT NULL,
    standard_error double precision NOT NULL,
    n              int         NOT NULL,
    updated_at     timestamptz NOT NULL,
    PRIMARY KEY (user_id, section)
);

CREATE TABLE build_sentence_result (
    user_id    uuid        NOT NULL,
    attempt_id uuid        NOT NULL,
    correct    jsonb       NOT NULL,
    scored_at  timestamptz NOT NULL,
    PRIMARY KEY (user_id, attempt_id)
);

CREATE TABLE learner_onboarding (
    user_id      uuid PRIMARY KEY,
    completed_at timestamptz NOT NULL
);

CREATE TABLE band_estimate (
    user_id    uuid PRIMARY KEY,
    estimate   jsonb       NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE band_estimate_week (
    user_id    uuid  NOT NULL,
    week_start date  NOT NULL,
    estimate   jsonb NOT NULL,
    PRIMARY KEY (user_id, week_start)
);

CREATE TABLE tutor_student_link (
    tutor_id   uuid        NOT NULL,
    student_id uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tutor_id, student_id)
);
