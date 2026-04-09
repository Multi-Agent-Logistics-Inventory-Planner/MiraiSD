-- V18: Add assistant_manager to users role check constraint
-- Enables the new ASSISTANT_MANAGER role for users

DO $$
BEGIN
    -- Update users.role check constraint if the table exists
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'users'
    ) THEN
        BEGIN
            ALTER TABLE public.users
                DROP CONSTRAINT IF EXISTS users_role_check;
        EXCEPTION
            WHEN undefined_object THEN
                NULL;
        END;

        -- Recreate with assistant_manager added
        ALTER TABLE public.users
            ADD CONSTRAINT users_role_check
            CHECK (
                role::text = ANY (
                    ARRAY[
                        'admin',
                        'assistant_manager',
                        'employee'
                    ]::text[]
                )
            );
    END IF;
END $$;
