CREATE TABLE dl_model_config (
    id            BIGSERIAL PRIMARY KEY,
    model_name    VARCHAR(255) NOT NULL UNIQUE,
    model_version VARCHAR(100),
    temperature   FLOAT        NOT NULL DEFAULT 0.0,
    max_tokens    INTEGER      NOT NULL DEFAULT 512,
    source        VARCHAR(20)  NOT NULL DEFAULT 'HUGGINGFACE',
    local_path    VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Initiale Einträge (Evaluierungsphase)
INSERT INTO dl_model_config (model_name, model_version, temperature, max_tokens, source, active)
VALUES
    ('gelectra-base-germanquad', '2024-03', 0.0, 512, 'HUGGINGFACE', true),
    ('german-roberta-squad2',    '2024-03', 0.0, 512, 'HUGGINGFACE', true);
