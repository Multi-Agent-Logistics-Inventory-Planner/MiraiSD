-- Add read_at column for tracking when user marks notification as read in UI
-- This is separate from delivered_at (Slack delivery) and resolved_at (archived)
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP WITH TIME ZONE;

-- Index for efficiently querying unread notifications
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications (created_at) WHERE read_at IS NULL;
