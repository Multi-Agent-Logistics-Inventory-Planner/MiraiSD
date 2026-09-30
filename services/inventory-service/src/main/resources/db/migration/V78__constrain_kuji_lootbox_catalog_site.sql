-- Human-gated post-deploy constraint (.specs/phase-7-kuji-lootbox T-2/T-3, matching V68's
-- precedent). Do not include V78 in an automatic Flyway target until V75/V76's expand/backfill
-- are verified, the kuji/lootbox module's site-aware writers are deployed, and an immediate
-- production check confirms none of these columns have NULLs.
--
-- Constrains the catalog side only (kuji_boxes, kuji_box_tiers, lootboxes, lootbox_tiers,
-- lootbox_prizes). The coin ledger (lootbox_plays, coin_adjustments, review_daily_counts) is
-- constrained separately in V79 because it also depends on the messaging-service redeploy and
-- because lootbox_plays' composite FK references lootboxes(id, site_id), which must exist first.

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

-- Replace the plain parent FKs with same-site composite FKs, using the (id, site_id) unique
-- indexes V77 already built CONCURRENTLY. This closes the "prize repointed into another site's
-- crate via an unscoped tier_id update" gap identified in T-2 (LootboxAdminService.updatePrize) at
-- the database layer, on top of the application-level site check T-3 adds.
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

-- kuji_boxes site-consistency trigger: validates (does not derive) that a box's site_id always
-- matches its location's site, and its machine display's site when one is attached. locations has
-- no site_id column of its own (see V67's comment), so a composite FK on location_id is not
-- possible - this trigger is the enforcement mechanism instead. It is defense in depth only:
-- application code (T-3) must still resolve the location through a site-scoped lookup before this
-- ever fires. machine_display.site_id is still nullable at this point (its own constrain step is
-- gated separately in .specs/phase-7-displays), so the display check is skipped when that column
-- is NULL rather than treated as a mismatch.
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
