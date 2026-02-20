-- Review tracking tables for employee review mentions
-- Used by Python messaging service (not managed by JPA)

-- Employee names with variant spellings for matching
CREATE TABLE IF NOT EXISTS review_employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_name VARCHAR(100) NOT NULL UNIQUE,
    name_variants TEXT[] NOT NULL DEFAULT '{}',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for looking up by canonical name
CREATE INDEX IF NOT EXISTS idx_review_employees_canonical_name
    ON review_employees (canonical_name);

-- Daily review counts per employee
CREATE TABLE IF NOT EXISTS review_daily_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES review_employees(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    review_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(employee_id, date)
);

-- Index for efficient monthly aggregations
CREATE INDEX IF NOT EXISTS idx_review_daily_counts_date
    ON review_daily_counts (date);

CREATE INDEX IF NOT EXISTS idx_review_daily_counts_employee_date
    ON review_daily_counts (employee_id, date);

-- Individual reviews for drill-down
CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(255) UNIQUE,
    employee_id UUID REFERENCES review_employees(id) ON DELETE SET NULL,
    review_date DATE NOT NULL,
    review_text TEXT,
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    reviewer_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for querying reviews by employee and date
CREATE INDEX IF NOT EXISTS idx_reviews_employee_date
    ON reviews (employee_id, review_date);

-- Index for querying reviews by date range
CREATE INDEX IF NOT EXISTS idx_reviews_date
    ON reviews (review_date);

-- Seed initial employee data from existing NAMES configuration
INSERT INTO review_employees (canonical_name, name_variants) VALUES
    ('AJ', ARRAY['AJ']),
    ('Angelina', ARRAY['Angelina']),
    ('Averey', ARRAY['Averey', 'Avery']),
    ('Cathy', ARRAY['Cathy', 'Kathy']),
    ('Christine', ARRAY['Christine', 'Christina', 'Kristine', 'Kristin']),
    ('Doan', ARRAY['Doan', 'Down']),
    ('Dorothy', ARRAY['Dorothy']),
    ('Emma', ARRAY['Emma', 'Ema']),
    ('Grace', ARRAY['Grace']),
    ('Isaack', ARRAY['Isaack', 'Isaak', 'Isaac', 'Isack', 'Isac', 'Issack', 'Issac', 'Issak', 'Issaack']),
    ('Lucas', ARRAY['Lucas']),
    ('Matthew', ARRAY['Matthew', 'Mathew', 'Matt']),
    ('Mina', ARRAY['Mina']),
    ('Quincy', ARRAY['Quincy']),
    ('Sissy', ARRAY['Sissy']),
    ('Victoria', ARRAY['Victoria']),
    ('Yixin', ARRAY['Yixin', 'Toxin']),
    ('Eric', ARRAY['Eric'])
ON CONFLICT (canonical_name) DO UPDATE SET
    name_variants = EXCLUDED.name_variants,
    updated_at = NOW();
