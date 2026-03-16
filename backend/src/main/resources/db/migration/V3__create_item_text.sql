CREATE TABLE item_text (
    id             BIGSERIAL PRIMARY KEY,
    wh_listing_id  BIGINT REFERENCES wh_listing(id) ON DELETE SET NULL,
    source         VARCHAR(50) NOT NULL,
    title          TEXT        NOT NULL,
    description    TEXT        NOT NULL,
    content_hash   VARCHAR(64) NOT NULL,
    fetched_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_item_text_wh_listing ON item_text(wh_listing_id);
