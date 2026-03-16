-- Upgrade from two base models to single large model for better extraction quality.
-- gelectra-base-germanquad (110M params) → gelectra-large-germanquad (335M params)
-- bert-multi-english-german-squad2 removed (replaced by higher-quality gelectra-large)

UPDATE dl_model_config
SET model_name    = 'gelectra-large-germanquad',
    model_version = '2026-03'
WHERE model_name = 'gelectra-base-germanquad';

-- Deactivate bert-multi (no longer shipped)
UPDATE dl_model_config
SET active = false
WHERE model_name = 'bert-multi-english-german-squad2';

-- Clean existing extraction data (model changed, old results invalid)
DELETE FROM dl_extraction_term;
DELETE FROM dl_extraction_run;
