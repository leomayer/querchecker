-- Tags-Infrastruktur entfernen (Frontend + Backend-Endpoint bereits entfernt)
DROP TABLE IF EXISTS wh_item_tag;

-- Lokale LLM-Modelle deaktivieren (source = 'LOCAL')
UPDATE dl_model_config SET active = false WHERE source = 'LOCAL';
