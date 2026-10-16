-- Nullable on purpose: V70 backfills existing rows, and a default would hide unresolved ones.
-- webhook_events.site_id stays nullable; most EasyPost events have no shipment to derive a site from.
ALTER TABLE shipments ADD COLUMN site_id UUID;
ALTER TABLE shipment_items ADD COLUMN site_id UUID;
ALTER TABLE shipment_item_allocations ADD COLUMN site_id UUID;
ALTER TABLE webhook_events ADD COLUMN site_id UUID;
