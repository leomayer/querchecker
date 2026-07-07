CREATE TABLE access_key (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    secret_key_hash VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    quota_limit INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    last_used_at TIMESTAMP,
    used BOOLEAN NOT NULL DEFAULT false,
    revoked BOOLEAN NOT NULL DEFAULT false
);
