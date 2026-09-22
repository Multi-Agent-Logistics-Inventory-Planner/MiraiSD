-- Read-only assertions: failures abort setup or --check instead of reporting false success.
DO $$
DECLARE r record;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sites WHERE code='MAIN') THEN
        RAISE EXCEPTION 'MAIN site is missing';
    END IF;
    FOR r IN SELECT * FROM (VALUES
        ('stock_movements','site_id','uuid'),
        ('event_outbox','site_id','uuid'), ('event_outbox','event_version','integer'),
        ('event_outbox','correlation_id','text'), ('event_outbox','causation_id','text'),
        ('event_outbox','idempotency_key','text'),
        ('command_idempotency','id','uuid'), ('command_idempotency','site_id','uuid'),
        ('command_idempotency','user_id','uuid'), ('command_idempotency','idempotency_key','text'),
        ('command_idempotency','command_type','text'), ('command_idempotency','request_fingerprint','text'),
        ('command_idempotency','result_status','integer'), ('command_idempotency','result_body','text'),
        ('command_idempotency','created_at','timestamp with time zone')
    ) AS expected(tbl,col,typ)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public'
            AND c.table_name=r.tbl AND c.column_name=r.col AND c.data_type=r.typ) THEN
            RAISE EXCEPTION 'Missing or incompatible column %.% (expected %)',r.tbl,r.col,r.typ;
        END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
        AND table_name='event_outbox' AND column_name='event_version' AND column_default='1') THEN
        RAISE EXCEPTION 'event_outbox.event_version DEFAULT 1 is missing';
    END IF;
    FOR r IN SELECT * FROM (VALUES
        ('command_idempotency','id','gen_random_uuid()'),
        ('command_idempotency','created_at','now()'),
        ('site_products','id','gen_random_uuid()'), ('user_site_memberships','id','gen_random_uuid()')
    ) AS expected(tbl,col,def)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public'
            AND c.table_name=r.tbl AND c.column_name=r.col AND c.column_default=r.def) THEN
            RAISE EXCEPTION 'Incorrect default on %.%', r.tbl,r.col;
        END IF;
    END LOOP;
    FOR r IN SELECT * FROM (VALUES
        ('idx_location_inventory_site_product','location_inventory','site_id, product_id',false),
        ('idx_stock_movements_site_at','stock_movements','site_id, at DESC',false),
        ('idx_stock_movements_site_item_at','stock_movements','site_id, item_id, at DESC',false),
        ('idx_event_outbox_entity_id','event_outbox','entity_id',false),
        ('idx_command_idempotency_site_user_key','command_idempotency','site_id, user_id, idempotency_key',true),
        ('idx_command_idempotency_created_at','command_idempotency','created_at',false)
    ) AS expected(idx,tbl,cols,uniq)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
            WHERE c.oid=to_regclass('public.' || r.idx) AND i.indrelid=to_regclass('public.' || r.tbl)
            AND i.indisvalid AND i.indisready AND i.indisunique=r.uniq AND i.indpred IS NULL
            AND pg_get_indexdef(i.indexrelid) LIKE '% USING btree (' || r.cols || ')') THEN
            RAISE EXCEPTION 'Missing or incompatible index %', r.idx;
        END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='location_inventory'::regclass
        AND tgname='trg_sync_inventory_site_id' AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='location_inventory'::regclass
        AND tgname='trg_check_display_only_inventory' AND tgenabled='O') THEN
        RAISE EXCEPTION 'Location inventory site/display triggers are missing';
    END IF;
    IF EXISTS (SELECT 1 FROM users u WHERE NOT EXISTS (SELECT 1 FROM user_site_memberships m
        JOIN sites s ON s.id=m.site_id WHERE s.code='MAIN' AND m.user_id=u.id)) THEN
        RAISE EXCEPTION 'MAIN membership backfill is incomplete';
    END IF;
    IF EXISTS (SELECT 1 FROM products p WHERE NOT EXISTS (SELECT 1 FROM site_products sp
        JOIN sites s ON s.id=sp.site_id WHERE s.code='MAIN' AND sp.product_id=p.id)) THEN
        RAISE EXCEPTION 'MAIN product backfill is incomplete';
    END IF;
    IF EXISTS (SELECT 1 FROM stock_movements WHERE site_id IS NULL) THEN
        RAISE EXCEPTION 'Stock movement site backfill is incomplete';
    END IF;
    IF EXISTS (SELECT 1 FROM event_outbox eo JOIN stock_movements sm
        ON sm.id::text=eo.payload->>'stock_movement_id'
        WHERE eo.published_at IS NULL AND eo.entity_type='stock_movement' AND eo.site_id IS NULL) THEN
        RAISE EXCEPTION 'Resolvable unpublished outbox sites remain unbackfilled';
    END IF;
END $$;
