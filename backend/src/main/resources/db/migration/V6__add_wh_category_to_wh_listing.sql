ALTER TABLE wh_listing
ADD COLUMN wh_category_id BIGINT REFERENCES wh_category(id) ON DELETE SET NULL;

CREATE INDEX idx_wh_listing_category ON wh_listing(wh_category_id);
