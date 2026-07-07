CREATE TABLE user_session (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    access_key_id BIGINT NOT NULL REFERENCES access_key(id),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_session_access_key ON user_session(access_key_id);
