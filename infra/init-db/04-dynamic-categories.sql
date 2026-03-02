-- Dynamic Categories Migration
-- Converts enum-based categories to database-driven categories

-- ============================================
-- 1. CREATE CATEGORIES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS public.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_categories_slug ON public.categories(slug);
CREATE INDEX IF NOT EXISTS idx_categories_active ON public.categories(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_categories_display_order ON public.categories(display_order);

-- ============================================
-- 2. CREATE SUBCATEGORIES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS public.subcategories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES public.categories(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT subcategories_category_slug_unique UNIQUE(category_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_subcategories_category_id ON public.subcategories(category_id);
CREATE INDEX IF NOT EXISTS idx_subcategories_active ON public.subcategories(is_active) WHERE is_active = true;

-- ============================================
-- 3. SEED EXISTING CATEGORIES FROM ENUM VALUES
-- ============================================
INSERT INTO public.categories (name, slug, display_order) VALUES
    ('Plushie', 'plushie', 1),
    ('Keychain', 'keychain', 2),
    ('Figurine', 'figurine', 3),
    ('Gachapon', 'gachapon', 4),
    ('Blind Box', 'blind-box', 5),
    ('Build Kit', 'build-kit', 6),
    ('Gundam', 'gundam', 7),
    ('Kuji', 'kuji', 8),
    ('Miscellaneous', 'miscellaneous', 9)
ON CONFLICT (slug) DO UPDATE SET
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    updated_at = NOW();

-- ============================================
-- 4. SEED EXISTING SUBCATEGORIES (BLIND BOX)
-- ============================================
INSERT INTO public.subcategories (category_id, name, slug, display_order)
SELECT
    c.id,
    sub.name,
    sub.slug,
    sub.display_order
FROM public.categories c
CROSS JOIN (VALUES
    ('Dreams', 'dreams', 1),
    ('Pokemon', 'pokemon', 2),
    ('Pop Mart', 'popmart', 3),
    ('Sanrio / San-X', 'sanrio-san-x', 4),
    ('52 Toys', 'fifty-two-toys', 5),
    ('Rolife', 'rolife', 6),
    ('Toy City', 'toy-city', 7),
    ('Miniso', 'miniso', 8),
    ('Miscellaneous', 'miscellaneous', 9)
) AS sub(name, slug, display_order)
WHERE c.slug = 'blind-box'
ON CONFLICT (category_id, slug) DO UPDATE SET
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    updated_at = NOW();

-- ============================================
-- 5-7. PRODUCTS TABLE MIGRATIONS
-- ============================================
-- Skip if products table doesn't exist yet (Hibernate will create it)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'products'
    ) THEN
        RAISE NOTICE 'products table does not exist yet; skipping migration (Hibernate will create it with category_id)';
        RETURN;
    END IF;

    -- Add category_id column if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
        AND table_name = 'products'
        AND column_name = 'category_id'
    ) THEN
        ALTER TABLE public.products ADD COLUMN category_id UUID REFERENCES public.categories(id) ON DELETE RESTRICT;
    END IF;

    -- Add subcategory_id column if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
        AND table_name = 'products'
        AND column_name = 'subcategory_id'
    ) THEN
        ALTER TABLE public.products ADD COLUMN subcategory_id UUID REFERENCES public.subcategories(id) ON DELETE SET NULL;
    END IF;

    -- Create indexes on FK columns
    CREATE INDEX IF NOT EXISTS idx_products_category_id ON public.products(category_id);
    CREATE INDEX IF NOT EXISTS idx_products_subcategory_id ON public.products(subcategory_id);

    -- Populate category_id from existing category string
    UPDATE public.products p
    SET category_id = c.id
    FROM public.categories c
    WHERE p.category = c.name
      AND p.category_id IS NULL;

    -- Populate subcategory_id from existing subcategory string
    UPDATE public.products p
    SET subcategory_id = s.id
    FROM public.subcategories s
    JOIN public.categories c ON s.category_id = c.id
    WHERE c.slug = 'blind-box'
      AND (
        (p.subcategory = 'Dreams' AND s.slug = 'dreams') OR
        (p.subcategory = 'Pokemon' AND s.slug = 'pokemon') OR
        (p.subcategory = 'Popmart' AND s.slug = 'popmart') OR
        (p.subcategory = 'Sanrio/San-X' AND s.slug = 'sanrio-san-x') OR
        (p.subcategory = '52 Toys' AND s.slug = 'fifty-two-toys') OR
        (p.subcategory = 'Rolife' AND s.slug = 'rolife') OR
        (p.subcategory = 'Toy City' AND s.slug = 'toy-city') OR
        (p.subcategory = 'Miniso' AND s.slug = 'miniso') OR
        (p.subcategory = 'Miscellaneous' AND s.slug = 'miscellaneous')
      )
      AND p.subcategory_id IS NULL;

    -- Make category_id NOT NULL if all products have been migrated
    IF NOT EXISTS (SELECT 1 FROM public.products WHERE category_id IS NULL) THEN
        ALTER TABLE public.products ALTER COLUMN category_id SET NOT NULL;
    ELSE
        RAISE NOTICE 'Some products still have NULL category_id - skipping NOT NULL constraint';
    END IF;
END $$;

-- ============================================
-- 8. DROP OLD COLUMNS (Run after backend deployment)
-- ============================================
-- Uncomment these after the backend is updated to use category_id/subcategory_id
-- ALTER TABLE public.products DROP CONSTRAINT IF EXISTS products_category_check;
-- ALTER TABLE public.products DROP CONSTRAINT IF EXISTS products_subcategory_check;
-- ALTER TABLE public.products DROP COLUMN IF EXISTS category;
-- ALTER TABLE public.products DROP COLUMN IF EXISTS subcategory;
-- DROP INDEX IF EXISTS idx_items_category;
