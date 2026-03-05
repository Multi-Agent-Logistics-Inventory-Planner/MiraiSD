-- Migration: Add damaged_quantity column to shipment_items
-- Damaged items are tracked separately and not added to inventory stock

ALTER TABLE shipment_items
    ADD COLUMN IF NOT EXISTS damaged_quantity INTEGER NOT NULL DEFAULT 0;
