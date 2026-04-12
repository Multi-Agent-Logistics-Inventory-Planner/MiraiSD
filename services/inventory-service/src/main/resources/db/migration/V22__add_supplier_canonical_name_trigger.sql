-- Function to canonicalize supplier names (lowercase, trim, collapse whitespace)
CREATE OR REPLACE FUNCTION canonicalize_supplier_name(name TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN LOWER(TRIM(REGEXP_REPLACE(name, '\s+', ' ', 'g')));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Trigger function to set canonical_name before insert/update
CREATE OR REPLACE FUNCTION set_supplier_canonical_name()
RETURNS TRIGGER AS $$
BEGIN
    NEW.canonical_name := canonicalize_supplier_name(NEW.display_name);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-set canonical_name on insert or when display_name changes
DROP TRIGGER IF EXISTS trigger_set_supplier_canonical_name ON suppliers;
CREATE TRIGGER trigger_set_supplier_canonical_name
    BEFORE INSERT OR UPDATE OF display_name ON suppliers
    FOR EACH ROW
    EXECUTE FUNCTION set_supplier_canonical_name();
