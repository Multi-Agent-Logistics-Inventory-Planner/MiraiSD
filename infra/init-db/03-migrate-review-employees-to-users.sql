-- Migration: Merge review_employees columns into users table
-- This allows review tracking to be associated with system users directly
--
-- NOTE: The users, reviews, and review_daily_counts tables are created by
-- Hibernate (ddl-auto=update) after app startup. This script is a no-op on
-- a fresh database; Hibernate creates the columns from the entity mappings.
-- It remains here to backfill the schema on an existing database where the
-- app was started before these columns were added to the entities.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'users'
    ) THEN
        RAISE NOTICE 'users table does not exist yet; skipping migration (Hibernate will create it)';
        RETURN;
    END IF;

    -- Step 1: Add new columns to users table
    ALTER TABLE users
        ADD COLUMN IF NOT EXISTS name_variants TEXT[] DEFAULT '{}',
        ADD COLUMN IF NOT EXISTS is_review_tracked BOOLEAN DEFAULT false;

    -- Step 2: Update foreign keys in reviews table to point to users
    ALTER TABLE reviews
        ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE SET NULL;

    -- Step 3: Update foreign keys in review_daily_counts table
    ALTER TABLE review_daily_counts
        ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;

    -- Step 4: Create indexes for the new columns
    CREATE INDEX IF NOT EXISTS idx_users_is_review_tracked
        ON users (is_review_tracked) WHERE is_review_tracked = true;

    CREATE INDEX IF NOT EXISTS idx_reviews_user_id
        ON reviews (user_id, review_date);

    CREATE INDEX IF NOT EXISTS idx_review_daily_counts_user_id
        ON review_daily_counts (user_id, date);

    RAISE NOTICE 'Migration 03 applied successfully';
END $$;

-- Note: Data migration from review_employees to users should be done manually
-- since we need to match employees to existing users by name.
--
-- After migration is complete and verified:
-- 1. Drop the old employee_id columns
-- 2. Drop the review_employees table
--
-- Example migration query (run manually after users exist):
-- UPDATE users u
-- SET name_variants = re.name_variants,
--     is_review_tracked = true
-- FROM review_employees re
-- WHERE LOWER(u.full_name) = LOWER(re.canonical_name);
