-- Re-seed PRODUCT_NAME prompts with improved system prompt.
-- DlCategoryPromptSeeder will regenerate them on next startup (seeds when count=0).
DELETE FROM dl_category_prompt WHERE prompt_type = 'PRODUCT_NAME';
