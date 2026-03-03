-- Machine Display tracking table
-- Tracks what product is displayed in each machine with history

CREATE TABLE IF NOT EXISTS machine_display (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_type VARCHAR(50) NOT NULL,
    machine_id UUID NOT NULL,
    product_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    actor_id UUID
);

-- Index for finding active display per machine (most common query)
CREATE INDEX IF NOT EXISTS idx_machine_display_active
    ON machine_display (location_type, machine_id, ended_at);

-- Index for finding stale displays
CREATE INDEX IF NOT EXISTS idx_machine_display_started_at
    ON machine_display (started_at);

-- Index for product history lookups
CREATE INDEX IF NOT EXISTS idx_machine_display_product
    ON machine_display (product_id);
