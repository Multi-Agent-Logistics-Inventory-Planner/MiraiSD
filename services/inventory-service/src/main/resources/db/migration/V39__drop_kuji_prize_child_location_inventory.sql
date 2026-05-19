-- Kuji prize children (any product whose parent is a non-CUSTOM kuji parent) become
-- tracking-only. Per-prize counts live on shipment_items.received_quantity; they no
-- longer have location_inventory rows.
--
-- CUSTOM kuji autoCreate children are out of scope -- KujiBoxService still writes
-- transient rows for them during close-box / patch-tier / add-slips flows. The
-- predicate (parent.kuji_type IS DISTINCT FROM 'CUSTOM') excludes that case while
-- catching the common case where kuji parents have parent.kuji_type=NULL (the
-- PREMADE tag is operationally unused -- vendor-shipped kuji sets live with NULL).

DELETE FROM location_inventory li
USING products child, products parent
WHERE child.id = li.product_id
  AND parent.id = child.parent_id
  AND parent.kuji_type IS DISTINCT FROM 'CUSTOM';

CREATE OR REPLACE FUNCTION reject_kuji_prize_child_in_location_inventory()
RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM products child
        JOIN products parent ON parent.id = child.parent_id
        WHERE child.id = NEW.product_id
          AND parent.kuji_type IS DISTINCT FROM 'CUSTOM'
    ) THEN
        RAISE EXCEPTION 'Kuji prize children do not track location inventory '
                        '(product_id=%). Edit shipment_items.received_quantity instead.',
                        NEW.product_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER location_inventory_no_kuji_prize_child
BEFORE INSERT OR UPDATE OF product_id ON location_inventory
FOR EACH ROW EXECUTE FUNCTION reject_kuji_prize_child_in_location_inventory();

COMMENT ON TRIGGER location_inventory_no_kuji_prize_child ON location_inventory IS
  'Kuji prize children (non-CUSTOM parent) are tracking-only; counts live on shipment_items.received_quantity.';
