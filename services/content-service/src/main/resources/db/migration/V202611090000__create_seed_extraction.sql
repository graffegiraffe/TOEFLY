-- How a seed object was extracted (docs/08-content-pipeline.md §4.7): method, review flag and warnings of the
-- extraction report, shown to the administrator who approves item sets and units waiting for review.
CREATE TABLE seed_extraction (
    target_id   uuid         PRIMARY KEY,
    seed_id     varchar(160) NOT NULL,
    method      varchar(12)  NOT NULL,
    reviewed    boolean      NOT NULL,
    warnings    jsonb        NOT NULL DEFAULT '[]'::jsonb,
    updated_at  timestamptz  NOT NULL
);
