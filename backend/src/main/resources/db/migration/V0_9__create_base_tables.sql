-- Pre-create JPA-managed tables that Flyway migrations reference before Hibernate
-- has a chance to run ddl-auto:update on a fresh database.
--
-- V1  → wh_category (FK)
-- V3  → wh_listing  (FK)
-- V6  → wh_listing  (ALTER), wh_category (FK)
-- V16 → app_config  (INSERT)
--
-- Hibernate ddl-auto:update fills in all real columns afterward.
-- IF NOT EXISTS makes every statement a no-op on existing databases
-- (safe for out-of-order apply after V18).

CREATE TABLE IF NOT EXISTS wh_category (
    id BIGSERIAL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS wh_listing (
    id BIGSERIAL PRIMARY KEY
);

-- app_config has a string PK and NOT NULL columns used by V16's INSERT,
-- so the full column set is required here.
CREATE TABLE IF NOT EXISTS app_config (
    key         VARCHAR(255) PRIMARY KEY,
    value       TEXT         NOT NULL,
    description TEXT,
    updated_at  TIMESTAMP    NOT NULL
);
