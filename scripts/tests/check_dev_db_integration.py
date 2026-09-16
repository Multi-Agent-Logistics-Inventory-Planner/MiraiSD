#!/usr/bin/env python3
"""Explicit local PostgreSQL integration checks. Every fixture is rolled back."""
import importlib.util
from pathlib import Path
import subprocess

spec = importlib.util.spec_from_file_location('setup_dev_db', Path(__file__).resolve().parents[1] / 'setup_dev_db.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


def main():
    target = setup.docker_target()
    body = setup.setup_sql().removeprefix('BEGIN;').removesuffix('COMMIT;')
    fixtures = """
    BEGIN;
    CREATE TEMP TABLE setup_fixture AS SELECT gen_random_uuid() AS product_id,
        gen_random_uuid() AS category_id, gen_random_uuid() AS event_id;
    INSERT INTO categories (id,name,slug)
        SELECT category_id,'Setup test',category_id::text FROM setup_fixture;
    INSERT INTO products (id,name,category_id,is_active,forecasting_enabled)
        SELECT product_id,'Setup test',category_id,true,true FROM setup_fixture;
    UPDATE user_site_memberships SET is_active=false WHERE site_id=(SELECT id FROM sites WHERE code='MAIN');
    INSERT INTO stock_movements (id,at,location_type,quantity_change,reason,item_id)
        SELECT -900000000001,now(),'NOT_ASSIGNED',5,'ADJUSTMENT',product_id FROM setup_fixture;
    INSERT INTO event_outbox (id,created_at,entity_type,entity_id,event_type,payload,publish_attempts,topic)
        SELECT event_id,now(),'stock_movement',product_id,'test',
            '{"stock_movement_id":"900000000001","correlation_id":"test-correlation"}'::jsonb,0,'test'
        FROM setup_fixture;
    """
    # Canonical V64 accepts positive movement IDs. Use a positive fixture ID and
    # fail rather than overwriting any collision in a previously seeded dev DB.
    fixtures = fixtures.replace('-900000000001', '900000000001')
    customize = """
    UPDATE site_products SET unit_cost=123.45, is_stocked=false
        WHERE product_id=(SELECT product_id FROM setup_fixture);
    """
    assertions = """
    DO $$
    BEGIN
        IF (SELECT count(*) FROM site_products WHERE product_id=(SELECT product_id FROM setup_fixture)) <> 1
           OR NOT EXISTS (SELECT 1 FROM site_products WHERE product_id=(SELECT product_id FROM setup_fixture)
               AND unit_cost=123.45 AND is_stocked=false) THEN
            RAISE EXCEPTION 'Rerun overwrote or duplicated site product settings';
        END IF;
        IF EXISTS (SELECT 1 FROM user_site_memberships WHERE is_active=true
            AND site_id=(SELECT id FROM sites WHERE code='MAIN')) THEN
            RAISE EXCEPTION 'Rerun reactivated existing membership';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM stock_movements WHERE id=900000000001 AND quantity_change=5
            AND site_id=(SELECT id FROM sites WHERE code='MAIN')) THEN
            RAISE EXCEPTION 'Movement backfill failed or quantity changed';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM event_outbox WHERE id=(SELECT event_id FROM setup_fixture)
            AND site_id=(SELECT id FROM sites WHERE code='MAIN')
            AND event_version=1 AND correlation_id='test-correlation') THEN
            RAISE EXCEPTION 'Outbox default/backfill failed';
        END IF;
    END $$;
    ROLLBACK;
    """
    setup.run(target, input=fixtures + body + customize + body + assertions, capture_output=True)
    print('PASS: repeat setup, defaults, movement/outbox backfill, override and inactive-membership preservation (rolled back).')

    for table in ('stock_movements', 'event_outbox'):
        if table == 'stock_movements':
            seed = fixtures + "INSERT INTO sites (id,name,code,country,created_at,updated_at) VALUES (gen_random_uuid(),'Setup second','SETUP_TEST_SECOND','USA',now(),now()); UPDATE stock_movements SET site_id=(SELECT id FROM sites WHERE code='SETUP_TEST_SECOND') WHERE id=900000000001;"
        else:
            seed = fixtures + "INSERT INTO sites (id,name,code,country,created_at,updated_at) VALUES (gen_random_uuid(),'Setup second','SETUP_TEST_SECOND','USA',now(),now()); UPDATE event_outbox SET site_id=(SELECT id FROM sites WHERE code='SETUP_TEST_SECOND') WHERE id=(SELECT event_id FROM setup_fixture);"
        try:
            setup.run(target, input=seed + body + 'ROLLBACK;', capture_output=True)
        except subprocess.CalledProcessError as exc:
            if 'Non-MAIN operational history exists' not in exc.stderr:
                print(exc.stderr)
                raise
        else:
            raise AssertionError(f'Non-MAIN {table} history was not rejected')
        print(f'PASS: non-MAIN {table} history rejected (connection exit rolls back fixtures).')

    verification = (setup.ROOT / 'scripts/sql/dev-db-verify.sql').read_text()
    setup.run(target, input='BEGIN READ ONLY;\n' + verification + """
    DO $$ BEGIN
        IF EXISTS (SELECT 1 FROM stock_movements WHERE id=900000000001) THEN
            RAISE EXCEPTION 'Integration fixture leaked';
        END IF;
    END $$;
    COMMIT;
    """, capture_output=True)
    print('PASS: live database still verifies and fixtures did not persist.')


if __name__ == '__main__':
    main()
