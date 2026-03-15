-- Migration: Add template_quantity to products for Kuji prize set calculations
-- Stores "quantity per set" for prize products (e.g., Prize A = 10 per set)

-- Add template_quantity column (nullable, only relevant for prize products)
ALTER TABLE products
ADD COLUMN IF NOT EXISTS template_quantity INTEGER;

-- Add check constraint to ensure non-negative values
ALTER TABLE products
ADD CONSTRAINT chk_template_quantity_non_negative
CHECK (template_quantity IS NULL OR template_quantity >= 0);
