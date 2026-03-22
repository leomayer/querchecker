CREATE TABLE category_spec_preference (
    id             BIGSERIAL PRIMARY KEY,
    wh_category_id BIGINT REFERENCES wh_category(id) ON DELETE CASCADE,
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_category_spec_preference_category UNIQUE (wh_category_id)
);

-- fieldKeys — via JPA @ElementCollection (like wh_item_tag)
CREATE TABLE category_spec_preference_field (
    preference_id BIGINT NOT NULL REFERENCES category_spec_preference(id) ON DELETE CASCADE,
    field_key     VARCHAR(100) NOT NULL
);
