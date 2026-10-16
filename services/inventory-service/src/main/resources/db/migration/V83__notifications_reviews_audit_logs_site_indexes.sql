-- Run outside a transaction (paired .conf): CONCURRENTLY avoids locking live tables.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_site_created
  ON notifications(site_id, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_site_recipient_created
  ON notifications(site_id, recipient_id, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_site_user_date
  ON reviews(site_id, user_id, review_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_site_created
  ON audit_logs(site_id, created_at DESC);
