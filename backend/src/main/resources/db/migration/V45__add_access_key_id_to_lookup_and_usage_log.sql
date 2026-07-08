ALTER TABLE listing_lookup_history ADD COLUMN access_key_id BIGINT REFERENCES access_key(id);
ALTER TABLE api_usage_log ADD COLUMN access_key_id BIGINT REFERENCES access_key(id);
