-- Enable source inheritance for Fernseher (TV) category
-- This allows child categories like Streaming-Geräte / Media Center to inherit lookup sources
UPDATE category_search_source
SET inherit_from_parent = true
WHERE wh_category_id = 660 AND source_type IN ('GENERIC', 'FLATPANELSHD');
