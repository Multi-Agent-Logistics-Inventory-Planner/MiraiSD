-- V35: Flag root categories whose products are sold in packs (TCG games).
--
-- Replaces the hardcoded "tcg" root-name detection used by the frontend's
-- buildPackCategoryIds. Setting uses_packs=true on a root category causes
-- the packs/box field to surface for products in that category or any of
-- its subcategories. The flag is meaningful only on root categories;
-- subcategories inherit at query time.

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS uses_packs BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE categories
SET uses_packs = TRUE
WHERE parent_id IS NULL
  AND LOWER(name) IN ('pokemon', 'one piece');
