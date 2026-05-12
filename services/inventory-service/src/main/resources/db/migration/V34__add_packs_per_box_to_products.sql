-- V34: Per-product pack-per-box multiplier.
--
-- For TCG SKUs (Pokemon, One Piece, future) where a single product row
-- represents one pack and a sealed box contains N of those packs.
-- Quantities (LocationInventory, StockMovement) remain in packs — this
-- value is purely an intake multiplier surfaced through the box/pack
-- toggle on quantity inputs. NULL means the product is not box-packaged
-- and the toggle should be hidden.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS packs_per_box INTEGER;
