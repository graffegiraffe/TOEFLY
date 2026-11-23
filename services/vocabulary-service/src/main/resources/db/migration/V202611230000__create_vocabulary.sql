-- vocabulary_db (docs/03 §6.11, §11): personal dictionary, FSRS cards and logs, review sessions, Collins word sets.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE vocabulary_entry (
    id              uuid PRIMARY KEY,
    user_id         uuid         NOT NULL,
    lemma           varchar(80)  NOT NULL,
    part_of_speech  varchar(16),
    definition_en   text,
    example_en      text,
    translation_ru  varchar(200),
    ipa             varchar(80),
    tts_key         char(64),
    note            varchar(500),
    contexts        jsonb        NOT NULL DEFAULT '[]'::jsonb,
    origin          varchar(14)  NOT NULL CHECK (origin IN ('TAP', 'KEY_WORDS', 'COLLINS_UNIT', 'MANUAL')),
    word_set_id     uuid,
    enriched        boolean      NOT NULL DEFAULT false,
    enrich_attempts smallint     NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         int          NOT NULL DEFAULT 0,
    -- INV-VOC-01: one entry per lemma of a learner
    CONSTRAINT uk_vocabulary_entry_lemma UNIQUE (user_id, lemma)
);
CREATE INDEX ix_vocabulary_entry_user_created ON vocabulary_entry (user_id, created_at DESC, id DESC);
CREATE INDEX ix_vocabulary_entry_lemma_trgm ON vocabulary_entry USING gin (lemma gin_trgm_ops);
CREATE INDEX ix_vocabulary_entry_unenriched ON vocabulary_entry (created_at) WHERE NOT enriched;

CREATE TABLE review_card (
    id                 uuid PRIMARY KEY,
    -- INV-VOC-02: exactly one card per entry, removed with it
    entry_id           uuid          NOT NULL UNIQUE REFERENCES vocabulary_entry (id) ON DELETE CASCADE,
    user_id            uuid          NOT NULL,
    state              varchar(12)   NOT NULL CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    due                timestamptz   NOT NULL,
    stability          numeric(9, 3) NOT NULL DEFAULT 0,
    difficulty         numeric(5, 3) NOT NULL DEFAULT 0,
    reps               int           NOT NULL DEFAULT 0,
    lapses             int           NOT NULL DEFAULT 0,
    last_review_at     timestamptz,
    learning_step      smallint      NOT NULL DEFAULT 0,
    suspended          boolean       NOT NULL DEFAULT false,
    fsrs_params_version varchar(20)  NOT NULL,
    created_at         timestamptz   NOT NULL,
    version            int           NOT NULL DEFAULT 0
);
CREATE INDEX ix_review_card_queue ON review_card (user_id, due) WHERE NOT suspended AND state <> 'NEW';
CREATE INDEX ix_review_card_new ON review_card (user_id, created_at) WHERE state = 'NEW';

CREATE TABLE review_session (
    id               uuid PRIMARY KEY,
    user_id          uuid        NOT NULL,
    week_task_id     uuid,
    local_date       date        NOT NULL,
    timezone         varchar(64) NOT NULL,
    planned_card_ids uuid[]      NOT NULL,
    reviewed_card_ids uuid[]     NOT NULL DEFAULT '{}',
    grades           int         NOT NULL DEFAULT 0,
    again_count      int         NOT NULL DEFAULT 0,
    new_introduced   int         NOT NULL DEFAULT 0,
    active_seconds   int         NOT NULL DEFAULT 0,
    status           varchar(12) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    started_at       timestamptz NOT NULL,
    completed_at     timestamptz,
    version          int         NOT NULL DEFAULT 0
);
CREATE INDEX ix_review_session_user_date ON review_session (user_id, local_date);

CREATE TABLE review_log (
    id                uuid PRIMARY KEY,
    card_id           uuid          NOT NULL REFERENCES review_card (id) ON DELETE CASCADE,
    user_id           uuid          NOT NULL,
    rating            smallint      NOT NULL CHECK (rating BETWEEN 1 AND 4),
    state_before      varchar(12)   NOT NULL,
    state_after       varchar(12)   NOT NULL,
    stability_before  numeric(9, 3) NOT NULL,
    difficulty_before numeric(5, 3) NOT NULL,
    stability_after   numeric(9, 3) NOT NULL,
    difficulty_after  numeric(5, 3) NOT NULL,
    elapsed_days      numeric(9, 3) NOT NULL,
    scheduled_days    numeric(9, 3) NOT NULL,
    session_id        uuid REFERENCES review_session (id) ON DELETE SET NULL,
    sequence_no       int           NOT NULL,
    reviewed_at       timestamptz   NOT NULL,
    duration_ms       int           NOT NULL DEFAULT 0
);
-- INV-VOC-04: a grade is counted once per (card, session, sequence number)
CREATE UNIQUE INDEX uk_review_log_once ON review_log (card_id, session_id, sequence_no) WHERE session_id IS NOT NULL;
CREATE INDEX ix_review_log_user_time ON review_log (user_id, reviewed_at);

-- Collins Vocabulary units as word sets; id = the unit id in content-service
CREATE TABLE word_set (
    id        uuid PRIMARY KEY,
    unit_no   smallint     NOT NULL UNIQUE CHECK (unit_no BETWEEN 1 AND 20),
    theme     varchar(20)  NOT NULL,
    title     varchar(200) NOT NULL,
    synced_at timestamptz  NOT NULL
);

CREATE TABLE word_set_word (
    word_set_id    uuid        NOT NULL REFERENCES word_set (id) ON DELETE CASCADE,
    ordinal        smallint    NOT NULL,
    lemma          varchar(80) NOT NULL,
    part_of_speech varchar(16) NOT NULL,
    definition_en  text        NOT NULL,
    example_en     text,
    ipa            varchar(80),
    PRIMARY KEY (word_set_id, ordinal)
);

-- INV-VOC-06: dictionary data of a lemma, shared by all learners (no user data)
CREATE TABLE dictionary_cache (
    lemma          varchar(80) PRIMARY KEY,
    part_of_speech varchar(16),
    definition_en  text,
    example_en     text,
    translation_ru varchar(200),
    ipa            varchar(80),
    source         varchar(16) NOT NULL CHECK (source IN ('AI', 'WORD_SET')),
    ai_stamp       jsonb,
    created_at     timestamptz NOT NULL
);

-- US-15.02: key words of an ItemSet text, cached per ItemSet for all learners
CREATE TABLE key_word_cache (
    item_set_id uuid PRIMARY KEY,
    words       jsonb       NOT NULL,
    ai_stamp    jsonb,
    created_at  timestamptz NOT NULL
);

-- Learner settings: zone from profile.preferences-updated, new cards per day (5–30)
CREATE TABLE learner_settings (
    user_id            uuid PRIMARY KEY,
    timezone           varchar(64) NOT NULL DEFAULT 'UTC',
    new_cards_per_day  smallint    NOT NULL DEFAULT 10 CHECK (new_cards_per_day BETWEEN 5 AND 30),
    updated_at         timestamptz NOT NULL
);
