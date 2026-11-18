-- Thresholds and per-case results of an eval run for the AI → Evals section (docs/05 §6.15, docs/12 phase 5b decision 15).
ALTER TABLE eval_run
    ADD COLUMN thresholds jsonb NOT NULL DEFAULT '{}',
    ADD COLUMN cases jsonb NOT NULL DEFAULT '[]';
