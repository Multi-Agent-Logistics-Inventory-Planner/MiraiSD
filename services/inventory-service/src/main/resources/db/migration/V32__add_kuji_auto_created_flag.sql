-- V32: Track whether a kuji tier's linked product was auto-created at open-box.
--
-- Auto-created prize products belong to that one box only. At close-box we
-- soft-delete the product (isActive=false) and hard-delete its image file.
-- Pre-existing linked products (flag=false) keep the existing close-box flow.

ALTER TABLE kuji_box_tiers
    ADD COLUMN IF NOT EXISTS auto_created_product BOOLEAN NOT NULL DEFAULT FALSE;
