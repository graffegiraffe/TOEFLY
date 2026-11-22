-- The model answer of the task of a submission, copied from content-service when the submission is created and when
-- a version is evaluated, so that getSubmissionInternal answers from assessment_db only (docs/05 §9.4, phase 5c).

CREATE TABLE submission_model_answer (
    submission_id uuid PRIMARY KEY REFERENCES submission (id) ON DELETE CASCADE,
    body          text NOT NULL,
    source        text
);
