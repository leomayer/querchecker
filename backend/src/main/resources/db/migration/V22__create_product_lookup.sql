CREATE TABLE product_lookup (
    id                     BIGSERIAL PRIMARY KEY,
    lookup_term            VARCHAR(255) NOT NULL,
    lookup_status          VARCHAR(20)  NOT NULL,
    quick_facts_json       TEXT,
    quick_facts_fetched_at TIMESTAMP,
    icecat_id              VARCHAR(100),
    icecat_url             VARCHAR(500),
    icecat_specs_json      TEXT,
    icecat_fetched_at      TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_lookup_term UNIQUE (lookup_term)
);

-- sourceUrls — via JPA @ElementCollection
CREATE TABLE product_lookup_source_url (
    product_lookup_id BIGINT NOT NULL REFERENCES product_lookup(id) ON DELETE CASCADE,
    source_url        VARCHAR(500) NOT NULL
);
