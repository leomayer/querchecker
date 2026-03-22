-- Add Groq API model to dl_model_config.
-- Runs first (execution_order=5) since API calls are faster than local models.
-- source='API' requires ModelSource.API enum value added alongside this migration.
INSERT INTO dl_model_config (model_name, model_version, temperature, max_tokens, source, local_path, active, execution_order)
VALUES ('groq', 'llama-3.1-8b-instant', 0.0, 128, 'API', null, true, 5);
