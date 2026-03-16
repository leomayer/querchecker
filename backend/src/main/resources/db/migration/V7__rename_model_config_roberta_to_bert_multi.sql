UPDATE dl_model_config
SET model_name = 'bert-multi-english-german-squad2',
    model_version = '2024-06'
WHERE model_name = 'german-roberta-squad2';
