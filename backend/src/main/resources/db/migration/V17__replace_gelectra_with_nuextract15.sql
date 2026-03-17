-- gelectra-large-germanquad offers no meaningful advantage over mdeberta-v3-base-squad2
-- and is 4x larger on disk. Replaced by NuExtract-v1.5 (1.5B structured extraction).
UPDATE dl_model_config SET active = false WHERE model_name = 'gelectra-large-germanquad';

INSERT INTO dl_model_config (model_name, model_version, temperature, max_tokens, source, local_path, active)
VALUES ('nuextract-1.5', '2024-12', 0.0, 128, 'LOCAL', 'src/main/resources/models/nuextract-1.5', true);
