CREATE TABLE access_key_usage (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    access_key_id BIGINT NOT NULL REFERENCES access_key(id),
    period_date DATE NOT NULL,
    consumed_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE (access_key_id, period_date)
);
