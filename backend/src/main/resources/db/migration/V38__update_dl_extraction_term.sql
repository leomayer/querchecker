-- Remove unused user-correction fields, add condensed_specs_json for structured listing specs
ALTER TABLE dl_extraction_term
    DROP COLUMN IF EXISTS user_corrected_term,
    DROP COLUMN IF EXISTS user_corrected_at,
    DROP COLUMN IF EXISTS correction_note,
    ADD COLUMN IF NOT EXISTS condensed_specs_json TEXT;
