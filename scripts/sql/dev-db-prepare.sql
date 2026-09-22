-- Only for the Hibernate-initialized local database, never a production migration.
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['sites','users','products','storage_locations','locations',
        'location_inventory','stock_movements','event_outbox','site_products','user_site_memberships']
    LOOP
        IF to_regclass('public.' || t) IS NULL THEN
            RAISE EXCEPTION 'Missing baseline table %. Start inventory-service with the dev Compose file first.', t;
        END IF;
    END LOOP;
    -- V60's MAIN fallback is valid only for a single-site legacy dataset.
    IF EXISTS (SELECT 1 FROM storage_locations sl JOIN sites s ON s.id=sl.site_id WHERE s.code <> 'MAIN')
       OR EXISTS (SELECT 1 FROM site_products sp JOIN sites s ON s.id=sp.site_id WHERE s.code <> 'MAIN') THEN
        RAISE EXCEPTION 'Non-MAIN operational data exists; refusing legacy MAIN backfills. Review migration manually.';
    END IF;
END $$;

INSERT INTO sites (id, name, code, country, created_at, updated_at)
VALUES (gen_random_uuid(), 'Main Store', 'MAIN', 'USA', now(), now())
ON CONFLICT (code) DO NOTHING;

ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS site_id UUID;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS site_id UUID;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS event_version INTEGER DEFAULT 1;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS correlation_id TEXT;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS causation_id TEXT;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS idempotency_key TEXT;
ALTER TABLE event_outbox ALTER COLUMN event_version SET DEFAULT 1;
ALTER TABLE event_outbox ALTER COLUMN correlation_id TYPE TEXT;
ALTER TABLE event_outbox ALTER COLUMN causation_id TYPE TEXT;
ALTER TABLE event_outbox ALTER COLUMN idempotency_key TYPE TEXT;

-- Explicit site-bound histories also disqualify the legacy single-site fallback,
-- even when their original locations or site assortment rows no longer exist.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM stock_movements sm WHERE sm.site_id IS NOT NULL
        AND sm.site_id <> (SELECT id FROM sites WHERE code='MAIN'))
       OR EXISTS (SELECT 1 FROM event_outbox eo WHERE eo.site_id IS NOT NULL
        AND eo.site_id <> (SELECT id FROM sites WHERE code='MAIN')) THEN
        RAISE EXCEPTION 'Non-MAIN operational history exists; refusing legacy MAIN backfills.';
    END IF;
END $$;
