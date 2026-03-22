-- Re-seed QUICK_FACTS prompts: clarify icecatId format (numeric ID at end of URL, not product code).
-- DlCategoryPromptSeeder regenerates on next startup when count=0.
DELETE FROM dl_category_prompt WHERE prompt_type = 'QUICK_FACTS';
