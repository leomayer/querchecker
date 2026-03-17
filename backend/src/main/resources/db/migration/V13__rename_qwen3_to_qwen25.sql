-- Qwen3-4B requires llama.cpp with qwen3 arch support (not yet in de.kherud:llama 4.2.0).
-- Replace with Qwen2.5-3B-Instruct which uses the supported qwen2 architecture.
UPDATE dl_model_config
SET model_name = 'qwen2.5-3b',
    local_path  = 'src/main/resources/models/qwen2.5-3b'
WHERE model_name = 'qwen3-4b';
