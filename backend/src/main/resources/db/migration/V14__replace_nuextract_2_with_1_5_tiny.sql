-- Replace NuExtract-2.0-4B (qwen2vl arch, incompatible with raw llama.cpp prompting)
-- with NuExtract-v1.5-tiny (qwen2 arch, 0.5B, same fine-tuning approach but simpler).
UPDATE dl_model_config
SET model_name = 'nuextract-1.5-tiny',
    local_path  = 'src/main/resources/models/nuextract-1.5-tiny',
    active      = true
WHERE model_name = 'nuextract-2.0-4b';
