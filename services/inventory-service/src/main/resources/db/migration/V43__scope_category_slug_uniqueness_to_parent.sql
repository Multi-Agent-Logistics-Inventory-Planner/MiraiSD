-- Categories were originally created with a global UNIQUE(slug). After the
-- self-referential parent_id model was introduced, uniqueness should be scoped
-- to (parent_id, slug) so a root category and a subcategory can share a name.
-- Drop any leftover single-column unique constraints/indexes on slug, and add
-- a partial unique index for root categories (parent_id IS NULL), since a
-- composite UNIQUE(parent_id, slug) does not enforce uniqueness when
-- parent_id is NULL in PostgreSQL.

DO $$
DECLARE
    constraint_rec RECORD;
BEGIN
    FOR constraint_rec IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY(con.conkey)
        WHERE rel.relname = 'categories'
          AND con.contype = 'u'
          AND att.attname = 'slug'
          AND array_length(con.conkey, 1) = 1
    LOOP
        EXECUTE 'ALTER TABLE categories DROP CONSTRAINT ' || quote_ident(constraint_rec.conname);
    END LOOP;
END $$;

DO $$
DECLARE
    index_rec RECORD;
BEGIN
    FOR index_rec IN
        SELECT i.relname AS index_name
        FROM pg_index ix
        JOIN pg_class i ON i.oid = ix.indexrelid
        JOIN pg_class t ON t.oid = ix.indrelid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
        WHERE t.relname = 'categories'
          AND ix.indisunique = true
          AND ix.indisprimary = false
          AND a.attname = 'slug'
          AND array_length(ix.indkey::int[], 1) = 1
    LOOP
        EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(index_rec.index_name);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS categories_root_slug_unique
    ON categories (slug)
    WHERE parent_id IS NULL;
