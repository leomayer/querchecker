INSERT INTO dl_model_config (model_name, model_version, temperature, max_tokens, source, local_path, active)
VALUES
    ('nuextract-2.0-4b', '2026-03', 0.0, 512, 'LOCAL', 'src/main/resources/models/nuextract-2.0-4b', true),
    ('qwen3-4b',         '2026-03', 0.0, 512, 'LOCAL', 'src/main/resources/models/qwen3-4b',         true);
