-- Migration: Widen letter column to allow prize labels like "Last Prize" (was VARCHAR(2) for A, B, C)
ALTER TABLE products
ALTER COLUMN letter TYPE VARCHAR(50);

COMMENT ON COLUMN products.letter IS 'Prize letter or label for Kuji children (e.g., A, B, C, Last Prize). Null for non-prize products.';
