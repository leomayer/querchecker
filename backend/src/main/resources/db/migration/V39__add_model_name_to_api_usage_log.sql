-- Track which LLM model was used per API call.
-- Enables per-model usage breakdown and rate-limit analysis in the Settings panel.
ALTER TABLE api_usage_log ADD COLUMN model_name VARCHAR(100);
