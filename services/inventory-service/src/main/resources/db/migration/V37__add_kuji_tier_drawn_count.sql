ALTER TABLE kuji_box_tiers
    ADD COLUMN drawn_count INTEGER NOT NULL DEFAULT 0;

WITH net_drawn AS (
    SELECT (sm.metadata->>'kuji_box_tier_id')::uuid AS tier_id,
           SUM(
               CASE
                   WHEN sm.reason = 'KUJI_PRIZE_WON'     THEN COALESCE((sm.metadata->>'slip_quantity')::int, 0)
                   WHEN sm.reason = 'KUJI_DRAW_REVERSED' THEN -COALESCE((sm.metadata->>'slip_quantity')::int, 0)
                   ELSE 0
               END
           ) AS net
    FROM stock_movements sm
    WHERE sm.reason IN ('KUJI_PRIZE_WON', 'KUJI_DRAW_REVERSED')
      AND sm.metadata->>'kuji_box_tier_id' IS NOT NULL
    GROUP BY sm.metadata->>'kuji_box_tier_id'
)
UPDATE kuji_box_tiers t
SET drawn_count = GREATEST(nd.net, 0)
FROM net_drawn nd
WHERE t.id = nd.tier_id;
