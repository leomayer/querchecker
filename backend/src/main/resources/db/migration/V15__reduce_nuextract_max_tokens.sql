-- NuExtract-1.5-tiny only needs to generate the product_name value (~64 tokens max).
-- The default 512 caused runaway hallucination filling all tokens with garbage fields.
UPDATE dl_model_config
SET max_tokens = 64
WHERE model_name = 'nuextract-1.5-tiny';
