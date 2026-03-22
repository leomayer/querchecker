CREATE TABLE api_usage_log (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(30) NOT NULL,
    request_type    VARCHAR(30) NOT NULL,
    lookup_term     VARCHAR(255),
    response_status INTEGER,
    tokens_input    INTEGER,
    tokens_output   INTEGER,
    duration_ms     BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_usage_log_provider_created ON api_usage_log (provider, created_at);
