-- Add execution_order to dl_model_config for sequential, ordered extraction runs.
-- Values are multiples of 10 to allow inserting new models between existing ones later.
-- NOT NULL without DEFAULT — every future INSERT must set execution_order explicitly.
ALTER TABLE dl_model_config ADD COLUMN execution_order INT NOT NULL DEFAULT 0;
ALTER TABLE dl_model_config ALTER COLUMN execution_order DROP DEFAULT;

-- Active models — run in this order
UPDATE dl_model_config SET execution_order = 10  WHERE model_name = 'llama-3.2-3b';
UPDATE dl_model_config SET execution_order = 20  WHERE model_name = 'qwen2.5-32b';
UPDATE dl_model_config SET execution_order = 30  WHERE model_name = 'mdeberta-v3-base-squad2';

-- Inactive models — assigned order for consistency if re-activated
UPDATE dl_model_config SET execution_order = 40  WHERE model_name = 'nuextract-1.5';
UPDATE dl_model_config SET execution_order = 50  WHERE model_name = 'nuextract-1.5-tiny';
UPDATE dl_model_config SET execution_order = 60  WHERE model_name = 'gelectra-large-germanquad';
UPDATE dl_model_config SET execution_order = 70  WHERE model_name = 'bert-multi-english-german-squad2';

-- Add duration_ms to dl_extraction_run to track how long each extraction took
ALTER TABLE dl_extraction_run ADD COLUMN duration_ms BIGINT;
