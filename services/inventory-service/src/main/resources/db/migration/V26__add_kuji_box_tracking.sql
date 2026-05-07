-- V26: Custom kuji prize tracking.
--
-- Adds:
--   * products.kuji_type (PREMADE | CUSTOM | NULL) and products.kuji_slack_webhook_url
--   * kuji_boxes table (one OPEN box per custom kuji at a time)
--   * kuji_box_tiers table (per-box tier definitions with optional product link)
--   * KUJI_PRIZE_WON / KUJI_DRAW_REVERSED stock movement reasons
--   * KUJI_PRIZE_DRAWN / KUJI_PRIZE_DRAW_UNDONE notification types
--
-- Backfill: existing kuji parents are left at kuji_type = NULL. Users opt in per kuji
-- via the product form. The Custom Kuji tab will be empty until at least one is marked.

-- 1. Mark kuji parent products as PREMADE or CUSTOM, plus per-kuji Slack webhook
ALTER TABLE products ADD COLUMN IF NOT EXISTS kuji_type VARCHAR(16);
ALTER TABLE products ADD COLUMN IF NOT EXISTS kuji_slack_webhook_url VARCHAR(500);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'products' AND constraint_name = 'chk_products_kuji_type'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_kuji_type
            CHECK (kuji_type IS NULL OR kuji_type IN ('PREMADE','CUSTOM'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'products' AND constraint_name = 'chk_products_kuji_type_root_only'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_kuji_type_root_only
            CHECK (kuji_type IS NULL OR parent_id IS NULL);
    END IF;
END $$;

-- 2. kuji_boxes: one row per opened physical custom kuji box
CREATE TABLE IF NOT EXISTS kuji_boxes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    location_id UUID NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    machine_display_id UUID REFERENCES machine_display(id) ON DELETE SET NULL,
    status VARCHAR(8) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    label VARCHAR(120),
    notes TEXT,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    opened_by UUID,
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Partial unique index: at most one OPEN box per kuji parent product
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kuji_boxes_open_per_product
    ON kuji_boxes (product_id) WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_kuji_boxes_product ON kuji_boxes (product_id, status);
CREATE INDEX IF NOT EXISTS idx_kuji_boxes_location ON kuji_boxes (location_id);
CREATE INDEX IF NOT EXISTS idx_kuji_boxes_machine_display ON kuji_boxes (machine_display_id)
    WHERE machine_display_id IS NOT NULL;

DROP TRIGGER IF EXISTS trg_kuji_boxes_updated_at ON kuji_boxes;
CREATE TRIGGER trg_kuji_boxes_updated_at
BEFORE UPDATE ON kuji_boxes
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- 3. kuji_box_tiers: tier definitions inside a box. Each row represents a pool of
-- identical slips at a given tier, optionally linked to a stocked product.
CREATE TABLE IF NOT EXISTS kuji_box_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    box_id UUID NOT NULL REFERENCES kuji_boxes(id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    letter VARCHAR(50),
    linked_product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    count INTEGER NOT NULL CHECK (count >= 0),
    price NUMERIC(10,2),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kuji_box_tiers_box ON kuji_box_tiers (box_id);
CREATE INDEX IF NOT EXISTS idx_kuji_box_tiers_linked_product
    ON kuji_box_tiers (linked_product_id) WHERE linked_product_id IS NOT NULL;

DROP TRIGGER IF EXISTS trg_kuji_box_tiers_updated_at ON kuji_box_tiers;
CREATE TRIGGER trg_kuji_box_tiers_updated_at
BEFORE UPDATE ON kuji_box_tiers
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- 4. Extend stock_movements.reason to include KUJI_PRIZE_WON, KUJI_DRAW_REVERSED
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS stock_movements_reason_check;

ALTER TABLE stock_movements
    ADD CONSTRAINT stock_movements_reason_check CHECK (reason IN (
        'INITIAL_STOCK',
        'RESTOCK',
        'SHIPMENT_RECEIPT',
        'SHIPMENT_RECEIPT_REVERSED',
        'SHIPMENT_PARTIAL_RECEIPT',
        'SHIPMENT_EDITED',
        'SHIPMENT_DELETED',
        'SHIPMENT_STATUS_OVERRIDDEN',
        'SALE',
        'DAMAGE',
        'ADJUSTMENT',
        'RETURN',
        'TRANSFER',
        'REMOVED',
        'DISPLAY_SET',
        'DISPLAY_REMOVED',
        'DISPLAY_SWAP',
        'KUJI_PRIZE_WON',
        'KUJI_DRAW_REVERSED'
    ));

-- 5. Extend notifications.type to include KUJI_PRIZE_DRAWN, KUJI_PRIZE_DRAW_UNDONE
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'notifications'
        AND constraint_name = 'notifications_type_check'
    ) THEN
        ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
    END IF;

    ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
        CHECK (type IN (
            'LOW_STOCK',
            'OUT_OF_STOCK',
            'REORDER_SUGGESTION',
            'EXPIRY_WARNING',
            'SYSTEM_ALERT',
            'SHIPMENT_COMPLETED',
            'SHIPMENT_DAMAGED',
            'SHIPMENT_DELIVERY_FAILED',
            'PACKAGE_ARRIVED',
            'DISPLAY_STALE',
            'DISPLAY_SET',
            'DISPLAY_REMOVED',
            'DISPLAY_SWAP',
            'DISPLAY_RENEWED',
            'KUJI_PRIZE_DRAWN',
            'KUJI_PRIZE_DRAW_UNDONE'
        ));
END $$;
