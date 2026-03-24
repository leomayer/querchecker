-- P8: Quellen-Tracking für ProductLookup (welche Quelle hat das Ergebnis geliefert)
ALTER TABLE product_lookup
    ADD COLUMN source_type   VARCHAR(30),
    ADD COLUMN source_domain VARCHAR,
    ADD COLUMN source_url    VARCHAR;

COMMENT ON COLUMN product_lookup.source_type   IS 'Quelle des Lookup-Ergebnisses (ICECAT, GSMARENA, FLATPANELSHD, GENERIC)';
COMMENT ON COLUMN product_lookup.source_domain IS 'Domain der Quelle (z.B. icecat.biz, gsmarena.com)';
COMMENT ON COLUMN product_lookup.source_url    IS 'Vollständige URL des relevantesten Treffers';
