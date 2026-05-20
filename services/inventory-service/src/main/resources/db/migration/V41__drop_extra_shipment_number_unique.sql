DO $$
DECLARE
    constraint_rec RECORD;
BEGIN
    FOR constraint_rec IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY(con.conkey)
        WHERE rel.relname = 'shipments'
          AND con.contype = 'u'
          AND att.attname = 'shipment_number'
          AND array_length(con.conkey, 1) = 1
    LOOP
        EXECUTE 'ALTER TABLE shipments DROP CONSTRAINT ' || quote_ident(constraint_rec.conname);
    END LOOP;
END $$;
