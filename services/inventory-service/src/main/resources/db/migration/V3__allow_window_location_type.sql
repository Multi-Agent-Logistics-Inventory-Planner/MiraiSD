-- V3: Allow WINDOW (and other new location types) in check constraints
-- Ensures stock_movements and shipment_item_allocations can use LocationType.WINDOW

DO $$
BEGIN
    -- Update stock_movements.location_type check constraint if the table exists
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'stock_movements'
    ) THEN
        BEGIN
            -- Drop the old constraint if it exists (name is from existing schema)
            ALTER TABLE public.stock_movements
                DROP CONSTRAINT IF EXISTS stock_movements_location_type_check;
        EXCEPTION
            WHEN undefined_object THEN
                -- Constraint didn't exist; ignore
                NULL;
        END;

        -- Recreate with the full set of supported location types
        ALTER TABLE public.stock_movements
            ADD CONSTRAINT stock_movements_location_type_check
            CHECK (
                location_type::text = ANY (
                    ARRAY[
                        'BOX_BIN',
                        'SINGLE_CLAW_MACHINE',
                        'DOUBLE_CLAW_MACHINE',
                        'KEYCHAIN_MACHINE',
                        'CABINET',
                        'RACK',
                        'FOUR_CORNER_MACHINE',
                        'PUSHER_MACHINE',
                        'WINDOW',
                        'NOT_ASSIGNED'
                    ]::text[]
                )
            );
    END IF;
END $$;


DO $$
BEGIN
    -- Update shipment_item_allocations.location_type check constraint if the table exists
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'shipment_item_allocations'
    ) THEN
        BEGIN
            ALTER TABLE public.shipment_item_allocations
                DROP CONSTRAINT IF EXISTS shipment_item_allocations_location_type_check;
        EXCEPTION
            WHEN undefined_object THEN
                NULL;
        END;

        ALTER TABLE public.shipment_item_allocations
            ADD CONSTRAINT shipment_item_allocations_location_type_check
            CHECK (
                location_type::text = ANY (
                    ARRAY[
                        'BOX_BIN',
                        'SINGLE_CLAW_MACHINE',
                        'DOUBLE_CLAW_MACHINE',
                        'KEYCHAIN_MACHINE',
                        'CABINET',
                        'RACK',
                        'FOUR_CORNER_MACHINE',
                        'PUSHER_MACHINE',
                        'WINDOW',
                        'NOT_ASSIGNED'
                    ]::text[]
                )
            );
    END IF;
END $$;

