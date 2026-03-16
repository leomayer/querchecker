CREATE TABLE dl_extraction_term (
    id                   BIGSERIAL PRIMARY KEY,
    run_id               BIGINT NOT NULL REFERENCES dl_extraction_run(id) ON DELETE CASCADE,
    term                 TEXT   NOT NULL,
    term_type            VARCHAR(100),
    confidence           FLOAT,
    user_corrected_term  TEXT,
    user_corrected_at    TIMESTAMP,
    correction_note      TEXT
);

CREATE INDEX idx_dl_extraction_term_run ON dl_extraction_term(run_id);
