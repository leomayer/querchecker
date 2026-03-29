-- TTL-Konfiguration für gecachte ProductLookup-Einträge
INSERT INTO app_config (key, value, description, updated_at) VALUES
    ('product.lookup.failed.ttl.hours', '24',
     'TTL in Stunden für gecachte FAILED-Lookups — nach Ablauf wird neu gesucht',
     NOW()),
    ('product.lookup.error.ttl.minutes', '10',
     'TTL in Minuten für gecachte ERROR-Lookups — nach Ablauf wird neu gesucht',
     NOW());
