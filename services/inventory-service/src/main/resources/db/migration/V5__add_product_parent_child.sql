-- Migration: Add parent-child relationship to products
-- Enables Kuji products to have prize children (A, B, C, etc.)

-- 1. Add parent_id column (nullable FK to self)
ALTER TABLE products
ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES products(id) ON DELETE SET NULL;

-- 2. Create index for efficient child lookups
CREATE INDEX IF NOT EXISTS idx_products_parent_id ON products(parent_id);

-- 3. Add comment for documentation
COMMENT ON COLUMN products.parent_id IS 'Reference to parent product (e.g., Kuji set). Null for standalone/root products.';
