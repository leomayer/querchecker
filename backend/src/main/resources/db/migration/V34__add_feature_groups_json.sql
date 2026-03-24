-- P8b: gruppierte Specs für GSMARENA / FlatpanelsHD (HTML-Fetch-Pfad)
ALTER TABLE product_lookup
    ADD COLUMN feature_groups_json TEXT;

COMMENT ON COLUMN product_lookup.feature_groups_json IS
    'JSON-Array mit Feature-Gruppen aus HTML-Fetch (GSMARENA, FLATPANELSHD) — null für Snippets-Pfad';
