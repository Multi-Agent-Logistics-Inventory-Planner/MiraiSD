-- V17: Add SHELF to location_type check constraints
-- Ensures stock_movements and shipment_item_allocations can use LocationType.SHELF

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
            ALTER TABLE public.stock_movements
                DROP CONSTRAINT IF EXISTS stock_movements_location_type_check;
        EXCEPTION
            WHEN undefined_object THEN
                NULL;
        END;

        -- Recreate with SHELF added
        ALTER TABLE public.stock_movements
            ADD CONSTRAINT stock_movements_location_type_check
            CHECK (
                location_type::text = ANY (
                    ARRAY[
                        'BOX_BIN',
                        'CABINET',
                        'DOUBLE_CLAW_MACHINE',
                        'FOUR_CORNER_MACHINE',
                        'GACHAPON',
                        'KEYCHAIN_MACHINE',
                        'PUSHER_MACHINE',
                        'RACK',
                        'SHELF',
                        'SINGLE_CLAW_MACHINE',
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

        -- Recreate with SHELF added
        ALTER TABLE public.shipment_item_allocations
            ADD CONSTRAINT shipment_item_allocations_location_type_check
            CHECK (
                location_type::text = ANY (
                    ARRAY[
                        'BOX_BIN',
                        'CABINET',
                        'DOUBLE_CLAW_MACHINE',
                        'FOUR_CORNER_MACHINE',
                        'GACHAPON',
                        'KEYCHAIN_MACHINE',
                        'PUSHER_MACHINE',
                        'RACK',
                        'SHELF',
                        'SINGLE_CLAW_MACHINE',
                        'WINDOW',
                        'NOT_ASSIGNED'
                    ]::text[]
                )
            );
    END IF;
END $$;
