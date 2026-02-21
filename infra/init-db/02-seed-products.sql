-- Seed Products for Testing
-- 8 products with varied characteristics for forecasting tests

INSERT INTO products (id, sku, name, category, unit_cost, is_active, lead_time_days, reorder_point, target_stock_level)
VALUES
    -- High demand products
    ('11111111-1111-1111-1111-111111111111', 'PLUSH-001', 'Pikachu Plush Large', 'Plush Toys', 15.99, true, 14, 50, 200),
    ('22222222-2222-2222-2222-222222222222', 'KEYCHAIN-001', 'Anime Keychain Set', 'Keychains', 3.99, true, 7, 100, 500),
    ('33333333-3333-3333-3333-333333333333', 'SNACK-001', 'Pocky Chocolate', 'Snacks', 2.49, true, 3, 150, 400),

    -- Medium demand products
    ('44444444-4444-4444-4444-444444444444', 'PLUSH-002', 'Totoro Plush Small', 'Plush Toys', 12.99, true, 14, 20, 80),
    ('55555555-5555-5555-5555-555555555555', 'CANDY-001', 'Hi-Chew Assorted', 'Candy', 1.99, true, 5, 80, 250),
    ('66666666-6666-6666-6666-666666666666', 'FIGURE-001', 'Anime Figure Blind Box', 'Figures', 8.99, true, 21, 15, 60),

    -- Low demand products
    ('77777777-7777-7777-7777-777777777777', 'KEYCHAIN-002', 'Premium Metal Keychain', 'Keychains', 7.99, true, 7, 10, 30),
    ('88888888-8888-8888-8888-888888888888', 'STICKER-001', 'Holographic Sticker Pack', 'Stickers', 4.99, true, 7, 20, 50);

-- Seed initial inventory in box_bin_inventory
INSERT INTO box_bin_inventory (item_id, quantity)
VALUES
    ('11111111-1111-1111-1111-111111111111', 75),   -- PLUSH-001: above reorder point
    ('22222222-2222-2222-2222-222222222222', 150),  -- KEYCHAIN-001: above reorder point
    ('33333333-3333-3333-3333-333333333333', 100),  -- SNACK-001: below reorder point (150)
    ('44444444-4444-4444-4444-444444444444', 30),   -- PLUSH-002: above reorder point
    ('55555555-5555-5555-5555-555555555555', 60),   -- CANDY-001: below reorder point (80)
    ('66666666-6666-6666-6666-666666666666', 20),   -- FIGURE-001: above reorder point
    ('77777777-7777-7777-7777-777777777777', 15),   -- KEYCHAIN-002: above reorder point
    ('88888888-8888-8888-8888-888888888888', 25);   -- STICKER-001: above reorder point
