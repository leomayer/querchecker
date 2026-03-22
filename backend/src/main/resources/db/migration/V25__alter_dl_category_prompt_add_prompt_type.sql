-- PromptType als VARCHAR (EnumType.STRING-Konvention des Projekts)
ALTER TABLE dl_category_prompt
    ADD COLUMN prompt_type   VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_NAME',
    ADD COLUMN system_prompt TEXT;

-- RENAME muss eigene ALTER TABLE-Anweisung sein (PostgreSQL-Einschränkung)
ALTER TABLE dl_category_prompt
    RENAME COLUMN prompt TO user_prompt;

-- Bestehende Einträge sind PRODUCT_NAME (Default greift)
-- Default danach entfernen — Pflichtfeld ab jetzt
ALTER TABLE dl_category_prompt
    ALTER COLUMN prompt_type DROP DEFAULT;

-- Unique Constraint: pro Kategorie+Typ genau ein Eintrag
-- null-Kategorie (Default) ist ebenfalls eindeutig pro Typ
ALTER TABLE dl_category_prompt
    DROP CONSTRAINT IF EXISTS uq_dl_category_prompt_category,
    ADD CONSTRAINT uq_dl_category_prompt_category_type
        UNIQUE (wh_category_id, prompt_type);
