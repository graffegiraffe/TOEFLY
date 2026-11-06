CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE source (
    id                 uuid         PRIMARY KEY,
    code               varchar(60)  NOT NULL UNIQUE,
    title              varchar(200) NOT NULL,
    publisher          varchar(100) NOT NULL,
    year               smallint,
    url                varchar(500),
    exam_format        varchar(12)  NOT NULL,
    legacy             boolean      NOT NULL,
    attribution_text   text         NOT NULL,
    license_note       text         NOT NULL DEFAULT 'THIRD_PARTY_ATTRIBUTION',
    extra              jsonb        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT source_legacy_matches_format CHECK (legacy = (exam_format = 'LEGACY'))
);

CREATE TABLE item_set (
    id                 uuid          PRIMARY KEY,
    seed_id            varchar(160)  UNIQUE,
    version            int           NOT NULL DEFAULT 1,
    section            varchar(10),
    task_type          varchar(40)   NOT NULL,
    exam_format        varchar(12)   NOT NULL,
    cefr               varchar(2),
    topic              varchar(120)  NOT NULL,
    domain             varchar(20),
    skills             text[]        NOT NULL DEFAULT '{}',
    source_id          uuid          NOT NULL REFERENCES source (id),
    source_page        int,
    source_item_no     varchar(40),
    provenance         varchar(16)   NOT NULL,
    status             varchar(12)   NOT NULL,
    hidden_reason      varchar(30),
    owner_tutor_id     uuid,
    b_mean             numeric(4, 2) NOT NULL DEFAULT 0,
    b_basis            varchar(12)   NOT NULL DEFAULT 'SEED_LABEL',
    ets_norm_seconds   int           NOT NULL,
    estimated_minutes  numeric(3, 1) NOT NULL,
    item_count         smallint      NOT NULL,
    payload            jsonb         NOT NULL,
    audio_state        varchar(12)   NOT NULL DEFAULT 'UNCHECKED',
    content_hash       char(64)      NOT NULL,
    search_text        text          NOT NULL DEFAULT '',
    search_tsv         tsvector GENERATED ALWAYS AS (
                           to_tsvector('english', coalesce(topic, '') || ' ' || coalesce(search_text, ''))) STORED,
    published_at       timestamptz,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    CONSTRAINT item_set_legacy_format CHECK ((exam_format = 'LEGACY') = (task_type LIKE 'LEGACY\_%'))
);
CREATE INDEX item_set_pick_idx ON item_set (task_type, cefr) WHERE status = 'APPROVED';
CREATE INDEX item_set_status_idx ON item_set (status, created_at, id);
CREATE INDEX item_set_source_idx ON item_set (source_id);
CREATE INDEX item_set_skills_idx ON item_set USING gin (skills);
CREATE INDEX item_set_tsv_idx ON item_set USING gin (search_tsv);

CREATE TABLE item (
    id           uuid         PRIMARY KEY,
    item_set_id  uuid         NOT NULL REFERENCES item_set (id) ON DELETE CASCADE,
    ordinal      smallint     NOT NULL,
    subskill     varchar(40),
    max_score    smallint     NOT NULL DEFAULT 1,
    b            numeric(5, 3),
    b_basis      varchar(12)  NOT NULL DEFAULT 'SEED_LABEL',
    UNIQUE (item_set_id, ordinal)
);

CREATE TABLE item_set_revision (
    item_set_id   uuid         NOT NULL REFERENCES item_set (id) ON DELETE CASCADE,
    version       int          NOT NULL,
    payload       jsonb        NOT NULL,
    content_hash  char(64)     NOT NULL,
    changed_by    uuid,
    created_at    timestamptz  NOT NULL,
    PRIMARY KEY (item_set_id, version)
);

CREATE TABLE item_set_embedding (
    item_set_id  uuid         NOT NULL REFERENCES item_set (id) ON DELETE CASCADE,
    chunk_no     smallint     NOT NULL,
    content      text         NOT NULL,
    embedding    vector(768)  NOT NULL,
    PRIMARY KEY (item_set_id, chunk_no)
);
CREATE INDEX item_set_embedding_idx ON item_set_embedding USING hnsw (embedding vector_cosine_ops);

CREATE TABLE item_report (
    id            uuid         PRIMARY KEY,
    item_set_id   uuid         NOT NULL REFERENCES item_set (id) ON DELETE CASCADE,
    item_ordinal  smallint,
    reporter_id   uuid         NOT NULL,
    reason        varchar(30)  NOT NULL,
    comment       text,
    status        varchar(12)  NOT NULL,
    resolved_by   uuid,
    created_at    timestamptz  NOT NULL,
    resolved_at   timestamptz
);
CREATE UNIQUE INDEX item_report_open_unique_idx
    ON item_report (item_set_id, coalesce(item_ordinal, -1), reporter_id) WHERE status = 'OPEN';
CREATE INDEX item_report_status_idx ON item_report (status, created_at);

CREATE TABLE seed_import (
    source_code    varchar(60)  PRIMARY KEY,
    bundle_sha256  varchar(80)  NOT NULL,
    status         varchar(12)  NOT NULL,
    counts         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    report         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    started_at     timestamptz  NOT NULL,
    finished_at    timestamptz
);

CREATE TABLE import_job (
    id           uuid         PRIMARY KEY,
    source_code  varchar(60),
    dry_run      boolean      NOT NULL,
    status       varchar(12)  NOT NULL,
    report       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at   timestamptz  NOT NULL,
    finished_at  timestamptz
);
