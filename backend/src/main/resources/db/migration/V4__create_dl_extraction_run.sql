CREATE TABLE dl_extraction_run (
    id               BIGSERIAL PRIMARY KEY,
    item_text_id     BIGINT NOT NULL REFERENCES item_text(id) ON DELETE CASCADE,
    model_config_id  BIGINT NOT NULL REFERENCES dl_model_config(id),
    prompt           TEXT   NOT NULL,
    input_hash       VARCHAR(64) NOT NULL,
    extracted_at     TIMESTAMP,
    status           VARCHAR(20)  NOT NULL DEFAULT 'INIT',
    error_message    VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dl_extraction_run_item_text    ON dl_extraction_run(item_text_id);
CREATE INDEX idx_dl_extraction_run_model_config ON dl_extraction_run(model_config_id);
CREATE INDEX idx_dl_extraction_run_status       ON dl_extraction_run(status);
