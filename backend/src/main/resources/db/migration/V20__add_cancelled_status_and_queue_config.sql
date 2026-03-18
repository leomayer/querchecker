-- Add CANCELLED to the extraction_status PG enum
ALTER TYPE extraction_status ADD VALUE IF NOT EXISTS 'CANCELLED';

-- Initial AppConfig entry for the DL queue limit
INSERT INTO app_config (key, value, description, updated_at)
VALUES ('dl.queue.limit', '10', 'Max. wartende DL-Extraction Tasks (laufender Task zählt nicht)', NOW())
ON CONFLICT (key) DO NOTHING;
