CREATE TABLE dl_category_prompt (
    id             BIGSERIAL PRIMARY KEY,
    wh_category_id BIGINT REFERENCES wh_category(id) ON DELETE SET NULL,
    prompt         TEXT      NOT NULL,
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dl_category_prompt_category UNIQUE (wh_category_id)
);

CREATE INDEX idx_dl_category_prompt_category ON dl_category_prompt(wh_category_id);
