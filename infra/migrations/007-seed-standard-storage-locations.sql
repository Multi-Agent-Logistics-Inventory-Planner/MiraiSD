-- Migration: Seed all standard storage location types for existing sites
-- This ensures all sites have the complete set of fixed storage location types.
-- Storage location types are no longer user-creatable.

INSERT INTO storage_locations (id, site_id, name, code, code_prefix, icon, has_display, is_display_only, display_order)
SELECT
    gen_random_uuid(),
    s.id,
    v.name,
    v.code,
    v.code_prefix,
    v.icon,
    v.has_display,
    v.is_display_only,
    v.display_order
FROM sites s
CROSS JOIN (VALUES
    ('Box Bins', 'BOX_BINS', 'B', 'Box', false, false, 0),
    ('Cabinets', 'CABINETS', 'C', 'Archive', false, false, 1),
    ('Racks', 'RACKS', 'R', 'Layers', false, false, 2),
    ('Windows', 'WINDOWS', 'W', 'PanelsTopLeft', false, false, 3),
    ('Single Claw', 'SINGLE_CLAW', 'SC', 'Gamepad2', true, false, 4),
    ('Double Claw', 'DOUBLE_CLAW', 'DC', 'Gamepad', true, false, 5),
    ('Four Corner', 'FOUR_CORNER', 'FC', 'LayoutGrid', true, false, 6),
    ('Pusher', 'PUSHER', 'P', 'ChevronsRight', true, false, 7),
    ('Gachapon', 'GACHAPON', 'G', 'Disc3', true, true, 8),
    ('Keychain', 'KEYCHAIN', 'K', 'Key', true, true, 9),
    ('Not Assigned', 'NOT_ASSIGNED', 'NA', 'CircleHelp', false, false, 99)
) AS v(name, code, code_prefix, icon, has_display, is_display_only, display_order)
WHERE NOT EXISTS (
    SELECT 1 FROM storage_locations sl
    WHERE sl.site_id = s.id AND sl.code = v.code
);
