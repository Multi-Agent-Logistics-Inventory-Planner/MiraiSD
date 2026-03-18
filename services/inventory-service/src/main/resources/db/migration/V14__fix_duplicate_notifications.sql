-- Fix duplicate notifications and outbox events
-- This migration adds proper deduplication at two layers:
-- 1. event_outbox: Prevent duplicate Kafka events for the same stock movement
-- 2. notifications: Fix deduplication to work regardless of source_event_id

-- ============================================================================
-- LAYER 1: Prevent duplicate outbox events for the same stock movement
-- ============================================================================

-- Add unique index on stock_movement_id extracted from JSONB payload
-- This prevents creating multiple outbox events for the same stock movement
CREATE UNIQUE INDEX IF NOT EXISTS idx_event_outbox_stock_movement_dedupe
    ON event_outbox ((payload->>'stock_movement_id'))
    WHERE entity_type = 'stock_movement'
      AND payload->>'stock_movement_id' IS NOT NULL;

-- ============================================================================
-- LAYER 2: Fix notification deduplication
-- ============================================================================

-- The old index was: (source_event_id, dedupe_key)
-- Problem: Two different source_event_ids with the same dedupe_key both insert
-- Solution: Dedupe by dedupe_key alone (within same day to allow re-alerting)

-- Drop the old ineffective index
DROP INDEX IF EXISTS idx_notifications_dedupe;

-- Create new index that deduplicates by dedupe_key + date
-- This allows the same alert to fire again on different days, but prevents
-- duplicates within the same day (which is the actual problem)
CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_dedupe
    ON notifications (dedupe_key, (created_at::date))
    WHERE dedupe_key IS NOT NULL;

-- Add index for faster lookups by dedupe_key
CREATE INDEX IF NOT EXISTS idx_notifications_dedupe_key
    ON notifications (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
