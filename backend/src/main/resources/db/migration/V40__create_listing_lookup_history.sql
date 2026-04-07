CREATE TABLE listing_lookup_history (
    id              BIGSERIAL PRIMARY KEY,
    listing_id      BIGINT        NOT NULL REFERENCES wh_listing(id) ON DELETE CASCADE,
    lookup_term     VARCHAR(500)  NOT NULL,
    lookup_status   VARCHAR(20)   NOT NULL,
    quick_facts_json TEXT,
    icecat_id       VARCHAR(255),
    source_type     VARCHAR(50),
    source_domain   VARCHAR(255),
    site_label      VARCHAR(255),
    source_url      VARCHAR(1000),
    feature_groups_json TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_listing_lookup_history_listing_id ON listing_lookup_history(listing_id);
