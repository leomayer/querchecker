-- app_config has a string PK and NOT NULL columns used by V16's INSERT,
-- so the full column set is required here.
-- Separate from V0_9 so that databases which already applied the original V0_9
-- (without app_config) pick this up as a new migration without a checksum conflict.
-- IF NOT EXISTS is a no-op if Hibernate already created the table.
CREATE TABLE IF NOT EXISTS app_config (
    key         VARCHAR(255) PRIMARY KEY,
    value       TEXT         NOT NULL,
    description TEXT,
    updated_at  TIMESTAMP    NOT NULL
);
