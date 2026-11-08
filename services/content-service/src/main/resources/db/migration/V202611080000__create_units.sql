CREATE TABLE grammar_unit (
    id                 uuid         PRIMARY KEY,
    seed_id            varchar(160) NOT NULL UNIQUE,
    source_id          uuid         NOT NULL REFERENCES source (id),
    framework          varchar(20)  NOT NULL,
    unit_no            smallint     NOT NULL,
    title              varchar(200) NOT NULL,
    topic_codes        text[]       NOT NULL DEFAULT '{}',
    rule_card          jsonb,
    progress_check_no  smallint,
    exercises          jsonb        NOT NULL DEFAULT '[]'::jsonb,
    status             varchar(12)  NOT NULL,
    content_hash       char(64)     NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    UNIQUE (framework, unit_no)
);

CREATE TABLE vocabulary_unit (
    id            uuid         PRIMARY KEY,
    seed_id       varchar(160) NOT NULL UNIQUE,
    source_id     uuid         NOT NULL REFERENCES source (id),
    unit_no       smallint     NOT NULL UNIQUE,
    theme         varchar(20)  NOT NULL,
    title         varchar(200) NOT NULL,
    words         jsonb        NOT NULL,
    exercises     jsonb        NOT NULL DEFAULT '[]'::jsonb,
    status        varchar(12)  NOT NULL,
    content_hash  char(64)     NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL
);
