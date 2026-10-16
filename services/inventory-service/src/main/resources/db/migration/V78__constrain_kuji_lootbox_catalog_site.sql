-- Human-gated: apply only after V75/V76 are verified, site-aware writers are deployed, and no
-- NULL site_id remains in these tables.

ALTER TABLE kuji_boxes
    ADD CONSTRAINT fk_kuji_boxes_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE kuji_boxes
    ALTER COLUMN site_id SET NOT NULL;

ALTER TABLE kuji_box_tiers
    ADD CONSTRAINT fk_kuji_box_tiers_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE kuji_box_tiers
    ALTER COLUMN site_id SET NOT NULL;

ALTER TABLE lootboxes
    ADD CONSTRAINT fk_lootboxes_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE lootboxes
    ALTER COLUMN site_id SET NOT NULL;

ALTER TABLE lootbox_tiers
    ADD CONSTRAINT fk_lootbox_tiers_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE lootbox_tiers
    ALTER COLUMN site_id SET NOT NULL;

ALTER TABLE lootbox_prizes
    ADD CONSTRAINT fk_lootbox_prizes_site FOREIGN KEY (site_id) REFERENCES sites(id);
ALTER TABLE lootbox_prizes
    ALTER COLUMN site_id SET NOT NULL;

-- Composite FKs stop a child row from pointing at a parent in another site.
ALTER TABLE kuji_box_tiers DROP CONSTRAINT kuji_box_tiers_box_id_fkey;
ALTER TABLE kuji_box_tiers
    ADD CONSTRAINT fk_kuji_box_tiers_box_site
        FOREIGN KEY (box_id, site_id) REFERENCES kuji_boxes(id, site_id) ON DELETE CASCADE;

ALTER TABLE lootbox_tiers DROP CONSTRAINT lootbox_tiers_lootbox_id_fkey;
ALTER TABLE lootbox_tiers
    ADD CONSTRAINT fk_lootbox_tiers_lootbox_site
        FOREIGN KEY (lootbox_id, site_id) REFERENCES lootboxes(id, site_id);

ALTER TABLE lootbox_prizes DROP CONSTRAINT lootbox_prizes_tier_id_fkey;
ALTER TABLE lootbox_prizes
    ADD CONSTRAINT fk_lootbox_prizes_tier_site
        FOREIGN KEY (tier_id, site_id) REFERENCES lootbox_tiers(id, site_id) ON DELETE CASCADE;

-- locations has no site_id, so a composite FK is impossible; a trigger checks the box site.
-- The display check is skipped while machine_display.site_id is still nullable.
CREATE OR REPLACE FUNCTION check_kuji_box_site_consistency() RETURNS TRIGGER AS $$
DECLARE
    location_site_id UUID;
    display_site_id UUID;
BEGIN
    SELECT sl.site_id INTO location_site_id
    FROM locations l
    JOIN storage_locations sl ON sl.id = l.storage_location_id
    WHERE l.id = NEW.location_id;

    IF location_site_id IS NULL THEN
        RAISE EXCEPTION 'kuji_boxes.location_id % does not resolve to a site', NEW.location_id;
    END IF;

    IF location_site_id != NEW.site_id THEN
        RAISE EXCEPTION 'kuji_boxes.site_id % does not match location %''s site %',
            NEW.site_id, NEW.location_id, location_site_id;
    END IF;

    IF NEW.machine_display_id IS NOT NULL THEN
        SELECT md.site_id INTO display_site_id
        FROM machine_display md
        WHERE md.id = NEW.machine_display_id;

        IF display_site_id IS NOT NULL AND display_site_id != NEW.site_id THEN
            RAISE EXCEPTION 'kuji_boxes.site_id % does not match machine_display %''s site %',
                NEW.site_id, NEW.machine_display_id, display_site_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_kuji_boxes_site_consistency ON kuji_boxes;
CREATE TRIGGER trg_kuji_boxes_site_consistency
BEFORE INSERT OR UPDATE OF location_id, site_id, machine_display_id ON kuji_boxes
FOR EACH ROW
EXECUTE FUNCTION check_kuji_box_site_consistency();
