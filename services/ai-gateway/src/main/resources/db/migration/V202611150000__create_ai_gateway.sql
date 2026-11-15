-- Prompt registry, call journal, usage aggregates, response cache, quotas and eval runs of ai-gateway
-- (docs/06-ai-agent.md §2.5–2.9, docs/03-domain-model.md §6.16).

CREATE TABLE prompt_template (
    prompt_key      varchar(120) PRIMARY KEY,
    use_case        varchar(40)  NOT NULL,
    description     text         NOT NULL DEFAULT '',
    owner_service   varchar(60)  NOT NULL,
    active_version  int,
    canary_version  int,
    canary_percent  smallint     NOT NULL DEFAULT 0 CHECK (canary_percent BETWEEN 0 AND 100),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE prompt_version (
    prompt_key       varchar(120) NOT NULL REFERENCES prompt_template (prompt_key),
    version          int          NOT NULL CHECK (version >= 1),
    use_case         varchar(40)  NOT NULL,
    description      text         NOT NULL DEFAULT '',
    system_text      text         NOT NULL,
    user_template    text         NOT NULL,
    output_schema    text,
    params           jsonb        NOT NULL DEFAULT '{}',
    max_input_tokens int          NOT NULL CHECK (max_input_tokens > 0),
    untrusted        jsonb        NOT NULL DEFAULT '[]',
    guard_policy     varchar(30)  NOT NULL,
    eval_suite       varchar(60),
    status           varchar(12)  NOT NULL,
    source_hash      char(64)     NOT NULL,
    created_by       varchar(120) NOT NULL,
    created_at       timestamptz  NOT NULL,
    PRIMARY KEY (prompt_key, version)
);

-- Only masked bodies; the mapping of placeholders never reaches this table (INV-AIG-04).
CREATE TABLE ai_call (
    id                    varchar(40)  PRIMARY KEY,
    caller_service        varchar(60)  NOT NULL,
    use_case              varchar(40)  NOT NULL,
    feature               varchar(60),
    priority              varchar(12),
    user_id               uuid,
    prompt_key            varchar(120),
    prompt_version        int,
    provider              varchar(20)  NOT NULL,
    model                 varchar(120) NOT NULL,
    fallback_depth        smallint     NOT NULL,
    degraded              boolean      NOT NULL,
    cache_hit             boolean      NOT NULL,
    request_masked        jsonb,
    response_masked       text,
    input_tokens          int          NOT NULL DEFAULT 0,
    output_tokens         int          NOT NULL DEFAULT 0,
    audio_seconds         double precision NOT NULL DEFAULT 0,
    characters            int          NOT NULL DEFAULT 0,
    latency_ms            bigint       NOT NULL,
    shadow_cost_micro_usd bigint       NOT NULL DEFAULT 0,
    guard_verdict         text,
    outcome               varchar(20)  NOT NULL,
    error_code            varchar(40),
    trace_id              varchar(40),
    correlation_id        uuid,
    created_at            timestamptz  NOT NULL
);
CREATE INDEX ai_call_user_created_idx ON ai_call (user_id, created_at);
CREATE INDEX ai_call_use_case_created_idx ON ai_call (use_case, created_at);
CREATE INDEX ai_call_created_idx ON ai_call (created_at DESC, id DESC);

-- user_key is the user id, or '' for system calls, so the primary key stays total.
CREATE TABLE ai_usage_daily (
    day                   date         NOT NULL,
    user_key              varchar(36)  NOT NULL,
    feature               varchar(60)  NOT NULL,
    provider              varchar(20)  NOT NULL,
    model                 varchar(120) NOT NULL,
    use_case              varchar(40)  NOT NULL,
    calls                 bigint       NOT NULL DEFAULT 0,
    cache_hits            bigint       NOT NULL DEFAULT 0,
    fallbacks             bigint       NOT NULL DEFAULT 0,
    input_tokens          bigint       NOT NULL DEFAULT 0,
    output_tokens         bigint       NOT NULL DEFAULT 0,
    audio_seconds         double precision NOT NULL DEFAULT 0,
    characters            bigint       NOT NULL DEFAULT 0,
    shadow_cost_micro_usd bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (day, user_key, feature, provider, model, use_case)
);

CREATE TABLE ai_response_cache (
    cache_key      char(64)     PRIMARY KEY,
    content        text         NOT NULL,
    provider       varchar(20)  NOT NULL,
    model          varchar(120) NOT NULL,
    prompt_key     varchar(120) NOT NULL,
    prompt_version int          NOT NULL,
    created_at     timestamptz  NOT NULL
);

CREATE TABLE provider_quota (
    provider   varchar(20)  NOT NULL,
    model      varchar(120) NOT NULL,
    dimension  varchar(30)  NOT NULL,
    capacity   bigint       NOT NULL CHECK (capacity > 0),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, model, dimension)
);

CREATE TABLE eval_run (
    id             uuid         PRIMARY KEY,
    prompt_key     varchar(120) NOT NULL,
    prompt_version int          NOT NULL,
    suite          varchar(60)  NOT NULL,
    mode           varchar(20)  NOT NULL,
    passed         boolean      NOT NULL,
    metrics        jsonb        NOT NULL DEFAULT '{}',
    report_url     text,
    ran_at         timestamptz  NOT NULL,
    received_at    timestamptz  NOT NULL,
    FOREIGN KEY (prompt_key, prompt_version) REFERENCES prompt_version (prompt_key, version)
);
CREATE INDEX eval_run_prompt_idx ON eval_run (prompt_key, prompt_version, received_at DESC);
