-- Notifications table for messaging service
-- This table is used by the Python messaging service (not managed by JPA)

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    item_id UUID,
    recipient_id UUID,
    inventory_id UUID,
    via TEXT[] DEFAULT ARRAY['slack', 'app'],
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    delivered_at TIMESTAMP WITH TIME ZONE,
    source_event_id UUID,
    dedupe_key VARCHAR(255),
    delivery_status VARCHAR(20) DEFAULT 'pending',
    delivery_claimed_at TIMESTAMP WITH TIME ZONE,
    delivery_attempts INTEGER DEFAULT 0
);

-- Index for efficient polling of undelivered notifications
CREATE INDEX IF NOT EXISTS idx_notifications_undelivered
    ON notifications (created_at)
    WHERE delivered_at IS NULL AND (delivery_status IS NULL OR delivery_status = 'pending');

-- Unique constraint for idempotency (prevent duplicate notifications from same event)
CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_dedupe
    ON notifications (source_event_id, dedupe_key)
    WHERE source_event_id IS NOT NULL;

-- Index for querying by item
CREATE INDEX IF NOT EXISTS idx_notifications_item_id ON notifications (item_id);
