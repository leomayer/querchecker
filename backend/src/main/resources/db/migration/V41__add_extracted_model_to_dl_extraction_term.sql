-- Add extractedModel column to dl_extraction_term
-- Stores the extracted product model name separately for UI display

ALTER TABLE dl_extraction_term
ADD COLUMN IF NOT EXISTS extracted_model TEXT;
