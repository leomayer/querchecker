-- NuExtract-1.5-tiny (0.5B) is too imprecise for reliable extraction.
-- NuExtract-1.5 (1.5B) replaced by Llama-3.2-3B-Instruct which handles German better.
UPDATE dl_model_config SET active = false
WHERE model_name IN ('nuextract-1.5-tiny', 'nuextract-1.5');

INSERT INTO dl_model_config (model_name, model_version, temperature, max_tokens, source, local_path, active)
VALUES ('llama-3.2-3b', '2024-09', 0.0, 128, 'LOCAL', 'src/main/resources/models/llama-3.2-3b', true);
