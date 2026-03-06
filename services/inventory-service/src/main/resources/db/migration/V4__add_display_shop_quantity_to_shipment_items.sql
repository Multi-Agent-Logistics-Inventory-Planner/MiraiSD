-- Migration: Add display_quantity and shop_quantity columns to shipment_items
-- Items assigned to Display or Shop are tracked separately and not added to inventory stock

ALTER TABLE shipment_items
    ADD COLUMN IF NOT EXISTS display_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE shipment_items
    ADD COLUMN IF NOT EXISTS shop_quantity INTEGER NOT NULL DEFAULT 0;
