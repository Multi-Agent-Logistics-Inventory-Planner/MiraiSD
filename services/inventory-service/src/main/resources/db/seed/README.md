# Database Seed Scripts

This directory contains SQL scripts to populate the database with sample data for development and testing.

## Available Seed Scripts

| Script | Purpose | Dependencies |
|--------|---------|--------------|
| `sales-seed-data.sql` | Generates stock movements (sales) for analytics | Products must exist |
| `forecast-seed-data.sql` | Generates forecast predictions for dashboard | Products must exist |
| `notifications-seed-data.sql` | Generates notifications for alerts | Products must exist |

## Running the Seeds

### Option 1: Using Supabase SQL Editor

1. Go to your Supabase Dashboard
2. Navigate to **SQL Editor**
3. Copy and paste each script in order
4. Run each script

### Option 2: Using psql

```bash
# Connect to your database
psql "postgresql://postgres:[PASSWORD]@[HOST]:5432/postgres"

# Run scripts in order
\i sales-seed-data.sql
\i forecast-seed-data.sql
\i notifications-seed-data.sql
```

### Option 3: Using the Supabase MCP Tool

If you're using Claude Code with the Supabase MCP:

```sql
-- Run forecast seed
-- (Copy content from forecast-seed-data.sql)
```

## Seed Data Distribution

### Forecast Predictions

The forecast seed distributes products across risk bands:

| Risk Band | Days to Stockout | % of Products |
|-----------|------------------|---------------|
| Critical (Urgent) | 0-1 days | 10% |
| Critical | 2-3 days | 10% |
| Warning | 4-7 days | 20% |
| Healthy | 8-30 days | 30% |
| Safe | 31-60 days | 20% |
| Overstocked | > 60 days | 10% |

### Notifications

Generated notification types:
- `LOW_STOCK` - Warning and Critical severity
- `OUT_OF_STOCK` - Critical severity
- `REORDER_SUGGESTION` - Info severity
- `SYSTEM_ALERT` - Info and Warning severity
- `UNASSIGNED_ITEM` - Warning severity

Approximately 1/3 of notifications are marked as resolved.

### Sales Data

- Generates 50-150 sales per product
- Spread over the last 365 days
- Random quantities between 1-5 units per sale

## Verifying Seed Data

Each script includes verification queries at the end. You can also run:

```sql
-- Check forecast distribution
SELECT
    CASE
        WHEN days_to_stockout <= 3 THEN 'Critical'
        WHEN days_to_stockout <= 7 THEN 'Warning'
        WHEN days_to_stockout <= 30 THEN 'Healthy'
        WHEN days_to_stockout <= 60 THEN 'Safe'
        ELSE 'Overstocked'
    END as risk_band,
    COUNT(*) as count
FROM forecast_predictions
WHERE computed_at = (SELECT MAX(computed_at) FROM forecast_predictions)
GROUP BY 1
ORDER BY
    CASE
        WHEN days_to_stockout <= 3 THEN 1
        WHEN days_to_stockout <= 7 THEN 2
        WHEN days_to_stockout <= 30 THEN 3
        WHEN days_to_stockout <= 60 THEN 4
        ELSE 5
    END;

-- Check notification counts
SELECT
    COUNT(*) FILTER (WHERE resolved_at IS NULL) as active,
    COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) as resolved
FROM notifications;
```

## Cleanup

To remove seeded data:

```sql
-- Remove seeded forecasts (keeps manually created ones)
DELETE FROM forecast_predictions
WHERE features->>'seed_generated' = 'true';

-- Remove seeded notifications
DELETE FROM notifications
WHERE metadata->>'seed_generated' = 'true';

-- Remove seeded stock movements
DELETE FROM stock_movements
WHERE metadata->>'source' = 'seed_data';
```
