CREATE TABLE category_search_source (
    id                  BIGSERIAL PRIMARY KEY,
    wh_category_id      BIGINT REFERENCES wh_category(id) ON DELETE CASCADE,
    priority            INTEGER     NOT NULL,
    site_domain         VARCHAR     NOT NULL,
    site_label          VARCHAR     NOT NULL,
    source_type         VARCHAR(30) NOT NULL,
    core_fields         TEXT[],
    query_excludes      TEXT[],
    search_result_count INTEGER     NOT NULL DEFAULT 10,
    lookup_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    inherit_from_parent BOOLEAN     NOT NULL DEFAULT FALSE,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_category_source UNIQUE (wh_category_id, site_domain)
);

COMMENT ON COLUMN category_search_source.lookup_enabled IS
    'true+coreFields=[...]: Lookup mit Pflichtfeldern | true+coreFields=[]: Lookup frei | false: kein Lookup';
COMMENT ON COLUMN category_search_source.inherit_from_parent IS
    'true: dieser level-1-Eintrag gilt als Fallback fuer level-2-Kategorien ohne eigenen Eintrag';
COMMENT ON COLUMN category_search_source.query_excludes IS
    'Negativ-Operatoren fuer Brave-Query, z.B. [-filetype:pdf, -review.php]. Werden unveraendert angehaengt.';
COMMENT ON COLUMN category_search_source.search_result_count IS
    '10 fuer Snippets-Pfad (alle ans LLM), 3 fuer HTML-Fetch-Pfad (Top-URL + Fallbacks)';

CREATE INDEX idx_css_category ON category_search_source(wh_category_id);
CREATE INDEX idx_css_domain   ON category_search_source(site_domain);
