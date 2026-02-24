-- Migration: Merge review_employees columns into users table
-- This allows review tracking to be associated with system users directly

-- Step 1: Add new columns to users table
ALTER TABLE users
ADD COLUMN IF NOT EXISTS name_variants TEXT[] DEFAULT '{}',
ADD COLUMN IF NOT EXISTS is_review_tracked BOOLEAN DEFAULT false;

-- Step 2: Update foreign keys in reviews table to point to users
-- First, add the new column
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
