-- Migration: Add letter column for Kuji prizes (A, B, C, etc.)
ALTER TABLE products
ADD COLUMN IF NOT EXISTS letter VARCHAR(2);

COMMENT ON COLUMN products.letter IS 'Prize letter for Kuji children (e.g., A, B, C). Null for non-prize products.';
