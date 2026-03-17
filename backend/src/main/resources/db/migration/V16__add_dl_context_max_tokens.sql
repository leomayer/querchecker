INSERT INTO app_config (key, value, description, updated_at)
VALUES ('dl.context.max_tokens', '512', 'Maximale Tokenanzahl für den Eingabe-Kontext aller AI-Extraktionsmodelle', NOW())
ON CONFLICT (key) DO NOTHING;
