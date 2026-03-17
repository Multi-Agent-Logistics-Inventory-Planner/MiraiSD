-- Add EasyPost tracker ID to shipments
ALTER TABLE shipments ADD COLUMN easypost_tracker_id VARCHAR(255);
CREATE INDEX idx_shipments_easypost_tracker_id ON shipments(easypost_tracker_id);

-- Create webhook events table for idempotency
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'easypost',
    payload JSONB NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_webhook_event_id UNIQUE (event_id, source)
);

CREATE INDEX idx_webhook_events_event_id ON webhook_events(event_id);
