-- Cleanup script: removes all DL extraction data for fresh testing
-- Run via: docker exec <container> psql -U myuser -d mydb -f /path/to/cleanup-dl-data.sql
-- Or copy-paste into psql

BEGIN;

-- 1. Remove extraction terms (FK → dl_extraction_run)
DELETE FROM dl_extraction_term;

-- 2. Remove extraction runs (FK → item_text, dl_model_config)
DELETE FROM dl_extraction_run;

-- 3. Remove item texts (FK → wh_listing)
DELETE FROM item_text;

-- 4. Remove category prompts (will be re-seeded on next startup/refresh)
DELETE FROM dl_category_prompt;

COMMIT;

-- Verify
SELECT 'dl_extraction_term' AS table_name, count(*) FROM dl_extraction_term
UNION ALL SELECT 'dl_extraction_run', count(*) FROM dl_extraction_run
UNION ALL SELECT 'item_text', count(*) FROM item_text
UNION ALL SELECT 'dl_category_prompt', count(*) FROM dl_category_prompt;
