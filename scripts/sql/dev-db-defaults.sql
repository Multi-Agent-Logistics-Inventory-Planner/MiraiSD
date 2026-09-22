-- Hibernate creates these mapped tables but does not install SQL defaults.
ALTER TABLE command_idempotency ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE command_idempotency ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE command_idempotency ALTER COLUMN idempotency_key TYPE TEXT;
ALTER TABLE command_idempotency ALTER COLUMN command_type TYPE TEXT;
ALTER TABLE command_idempotency ALTER COLUMN request_fingerprint TYPE TEXT;

ALTER TABLE user_site_memberships ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE user_site_memberships ALTER COLUMN is_active SET DEFAULT true;
ALTER TABLE user_site_memberships ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE user_site_memberships ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE user_site_memberships ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE site_products ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE site_products ALTER COLUMN is_stocked SET DEFAULT false;
ALTER TABLE site_products ALTER COLUMN forecasting_enabled SET DEFAULT true;
ALTER TABLE site_products ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE site_products ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE site_products ALTER COLUMN updated_at SET DEFAULT now();
CREATE INDEX IF NOT EXISTS idx_user_site_memberships_user ON user_site_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_user_site_memberships_site_active ON user_site_memberships(site_id, is_active);
CREATE INDEX IF NOT EXISTS idx_site_products_site ON site_products(site_id);
CREATE INDEX IF NOT EXISTS idx_site_products_site_stocked ON site_products(site_id, is_stocked);
CREATE INDEX IF NOT EXISTS idx_site_products_product ON site_products(product_id);
