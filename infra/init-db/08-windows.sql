-- Windows storage locations and inventory
-- Mirrors the pattern used by cabinets and cabinet_inventory

create table if not exists public.windows (
  id uuid not null default gen_random_uuid(),
  window_code character varying not null,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  constraint windows_pkey primary key (id),
  constraint windows_window_code_key unique (window_code),
  -- Match codes like W1, W2, W10, etc.
  -- Use [0-9]+ here to avoid any ambiguity with backslash escaping in regex literals.
  constraint windows_window_code_check check (((window_code)::text ~ '^W[0-9]+$'::text))
) tablespace pg_default;

DO $$
BEGIN
  -- Only create window_inventory once products table exists.
  -- In dev, Hibernate may create products (and window_inventory) later.
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'products'
  ) THEN
    CREATE TABLE IF NOT EXISTS public.window_inventory (
      id uuid not null default gen_random_uuid(),
      window_id uuid not null,
      item_id uuid not null,
      quantity integer not null default 0,
      created_at timestamp with time zone not null default now(),
      updated_at timestamp with time zone not null default now(),
      constraint window_inventory_pkey primary key (id),
      constraint window_inventory_unique_item unique (window_id, item_id),
      constraint window_inventory_window_id_fkey foreign key (window_id) references public.windows (id) on delete cascade,
      constraint window_inventory_item_id_fkey foreign key (item_id) references public.products (id) on delete restrict,
      constraint window_inventory_quantity_check check ((quantity >= 0))
    ) tablespace pg_default;

    CREATE INDEX IF NOT EXISTS idx_window_inventory_window_id
      ON public.window_inventory USING btree (window_id) tablespace pg_default;

    CREATE INDEX IF NOT EXISTS idx_window_inventory_item_id
      ON public.window_inventory USING btree (item_id) tablespace pg_default;
  ELSE
    RAISE NOTICE 'products table does not exist yet; skipping window_inventory creation (Hibernate will create it)';
  END IF;
END $$;

