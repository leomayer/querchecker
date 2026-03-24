-- P2b: Unified Attribute Architecture
-- coreFields zieht von CategorySearchSource in CategorySpecPreference (SYSTEM-Felder)

-- 1. core_fields aus category_search_source entfernen
ALTER TABLE category_search_source DROP COLUMN IF EXISTS core_fields;

-- 2. category_spec_preference_field: id PK + field_source ergänzen
ALTER TABLE category_spec_preference_field
    ADD COLUMN id          BIGSERIAL PRIMARY KEY;

ALTER TABLE category_spec_preference_field
    ADD COLUMN field_source VARCHAR(10) NOT NULL DEFAULT 'USER';

COMMENT ON COLUMN category_spec_preference_field.field_source IS
    'SYSTEM = geseedet (nicht editierbar) | USER = user-gepflegt via Settings';
