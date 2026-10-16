-- Nullable on purpose: V73 backfills existing rows, and a default would hide unresolved ones.
ALTER TABLE machine_display ADD COLUMN site_id UUID;
