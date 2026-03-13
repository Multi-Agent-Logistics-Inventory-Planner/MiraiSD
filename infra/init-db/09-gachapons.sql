-- Gachapon storage locations (display-only, no inventory)
-- Gachapons only track machine displays, not actual inventory storage

create table if not exists public.gachapons (
  id uuid not null default gen_random_uuid(),
  gachapon_code character varying not null,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  constraint gachapons_pkey primary key (id),
  constraint gachapons_gachapon_code_key unique (gachapon_code),
  -- Match codes like G1, G2, G10, etc.
  constraint gachapons_gachapon_code_check check (((gachapon_code)::text ~ '^G[0-9]+$'::text))
) tablespace pg_default;

-- Note: Gachapon is a display-only location type.
-- It does NOT have a gachapon_inventory table.
-- Products shown in gachapons are tracked via the machine_display table instead.

-- Update the resolve_location_code function to include GACHAPON
CREATE OR REPLACE FUNCTION resolve_location_code(
    p_location_id UUID,
    p_location_type TEXT
)
RETURNS TEXT AS $$
DECLARE
    result TEXT;
BEGIN
    IF p_location_id IS NULL THEN
        RETURN NULL;
    END IF;

    CASE p_location_type
        WHEN 'BOX_BIN' THEN
            SELECT box_bin_code INTO result FROM box_bins WHERE id = p_location_id;
        WHEN 'CABINET' THEN
            SELECT cabinet_code INTO result FROM cabinets WHERE id = p_location_id;
        WHEN 'DOUBLE_CLAW_MACHINE' THEN
            SELECT double_claw_machine_code INTO result FROM double_claw_machines WHERE id = p_location_id;
        WHEN 'FOUR_CORNER_MACHINE' THEN
            SELECT four_corner_machine_code INTO result FROM four_corner_machines WHERE id = p_location_id;
        WHEN 'GACHAPON' THEN
            SELECT gachapon_code INTO result FROM gachapons WHERE id = p_location_id;
        WHEN 'KEYCHAIN_MACHINE' THEN
            SELECT keychain_machine_code INTO result FROM keychain_machines WHERE id = p_location_id;
        WHEN 'PUSHER_MACHINE' THEN
            SELECT pusher_machine_code INTO result FROM pusher_machines WHERE id = p_location_id;
        WHEN 'RACK' THEN
            SELECT rack_code INTO result FROM racks WHERE id = p_location_id;
        WHEN 'SINGLE_CLAW_MACHINE' THEN
            SELECT single_claw_machine_code INTO result FROM single_claw_machines WHERE id = p_location_id;
        WHEN 'WINDOW' THEN
            SELECT window_code INTO result FROM windows WHERE id = p_location_id;
        WHEN 'NOT_ASSIGNED' THEN
            RETURN 'NA';
        ELSE
            RETURN NULL;
    END CASE;

    RETURN result;
END;
$$ LANGUAGE plpgsql;
