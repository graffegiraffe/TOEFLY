-- The learner model of tutor-agent from learner-model-updated (docs/06-ai-agent.md §5.2, docs/12 phase 5c decision 1).
-- One row per learner; the newer model version wins.
CREATE TABLE learner_model_view (
    user_id       uuid        PRIMARY KEY,
    version       bigint      NOT NULL,
    updated_at    timestamptz NOT NULL,
    mistake_stats jsonb       NOT NULL DEFAULT '[]',
    bands         jsonb       NOT NULL DEFAULT '{}',
    received_at   timestamptz NOT NULL DEFAULT now()
);
