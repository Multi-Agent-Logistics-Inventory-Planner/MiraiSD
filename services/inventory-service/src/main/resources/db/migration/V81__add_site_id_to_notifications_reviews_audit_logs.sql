-- Nullable on purpose: V82 backfills existing rows, and a default would hide unresolved ones.
ALTER TABLE notifications ADD COLUMN site_id UUID;
ALTER TABLE reviews ADD COLUMN site_id UUID;
ALTER TABLE audit_logs ADD COLUMN site_id UUID;
