-- P8: QUICK_FACTS-Prompts neu befüllen — icecatUrl → sourceUrl
-- Seeder schreibt beim Neustart neue Prompts mit dem aktualisierten Schema
DELETE FROM dl_category_prompt WHERE prompt_type = 'QUICK_FACTS';
