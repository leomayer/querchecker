-- Pre-create JPA-managed tables that early Flyway migrations reference via FK.
-- Hibernate ddl-auto:update adds all columns afterward; this only ensures the
-- primary keys exist before V1/V3/V6 run on a fresh database.
-- IF NOT EXISTS is a no-op on existing databases (out-of-order apply is safe).

CREATE TABLE IF NOT EXISTS wh_category (
    id BIGSERIAL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS wh_listing (
    id BIGSERIAL PRIMARY KEY
);
