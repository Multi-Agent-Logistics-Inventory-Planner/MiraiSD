# BFF Service

Backend-for-Frontend service for the Mirai inventory dashboard. Provides REST API endpoints for forecasts, inventory summaries, and item data.

## Prerequisites

- Node.js 20+
- Docker (for containerized deployment)
- Access to Supabase project

## Local Development

### 1. Install dependencies

```bash
cd bff-service
npm install
```

### 2. Configure environment

Copy the example env file and fill in your values:

```bash
cp env.example .env
```

Required variables:
- `DATABASE_URL` - Supabase PostgreSQL connection string (session pooler, port 5432)
- `SUPABASE_URL` - Your Supabase project URL
- `SUPABASE_ANON_KEY` - Supabase anon/public key
- `SUPABASE_JWT_SECRET` - Supabase JWT secret (for token verification)

Optional (local/dev):
- `AUTH_DISABLED` - Set to `true` to bypass Supabase auth checks locally (do not use in production)

### 3. Generate Prisma client

```bash
npm run prisma:generate
```

### 4. Start development server

```bash
npm run dev
```

Server runs at `http://localhost:5001`

## Testing Endpoints

### Health Check (no auth required)

```bash
curl http://localhost:5001/health
```

Expected response:
```json
{"status":"ok","timestamp":"2025-01-18T...","service":"bff-service"}
```

### Authenticated Endpoints

All other endpoints require a Supabase access token (unless `AUTH_DISABLED=true`). Get one by:
1. Logging in via your frontend app, or
2. Using Supabase client directly:

```javascript
import { createClient } from '@supabase/supabase-js'
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)
const { data } = await supabase.auth.signInWithPassword({ email, password })
console.log(data.session.access_token)
```

Then use the token:

```bash
export TOKEN="your-supabase-access-token"

# Get at-risk forecasts
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:5001/forecasts/at-risk?threshold=7&limit=10"

# Get forecast for specific item
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:5001/forecasts/YOUR-ITEM-UUID"

# Get inventory summary
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:5001/inventory/summary

# List items with forecasts
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:5001/items?includeForecasts=true&limit=10"

# Filter items by location type
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:5001/items?locationType=box_bin&limit=10"
```

### Local testing without Supabase login (dev only)

If you don't have a login flow yet, you can temporarily disable auth:

```bash
AUTH_DISABLED=true npm run dev
```

Then you can call endpoints without an `Authorization` header:

```bash
curl "http://localhost:5001/items?limit=10"
```

## Docker

### Build image

```bash
docker-compose build bff-service
```

### Run with Docker Compose

```bash
docker-compose up bff-service
```

### Run standalone

```bash
docker run -p 5001:5001 \
  -e DATABASE_URL="postgresql://..." \
  -e SUPABASE_URL="https://xxx.supabase.co" \
  -e SUPABASE_ANON_KEY="eyJ..." \
  -e SUPABASE_JWT_SECRET="..." \
  bff-service:latest
```

## API Reference

### GET /health
Health check endpoint.

### GET /forecasts/:itemId
Get the latest forecast for a specific item.

**Parameters:**
- `itemId` (path) - UUID of the item

**Response:** Forecast object with risk level

### GET /forecasts/at-risk
Get items at risk of stockout.

**Query Parameters:**
- `threshold` (default: 7) - Days to stockout threshold
- `limit` (default: 50, max: 100) - Results per page
- `offset` (default: 0) - Pagination offset

**Response:** Paginated list of forecasts

### GET /inventory/summary
Get aggregate inventory statistics.

**Response:**
```json
{
  "totalItems": 150,
  "totalQuantity": 5000,
  "atRiskCount": 12,
  "criticalCount": 3,
  "byLocation": {
    "boxBins": { "itemCount": 50, "totalQuantity": 1000 },
    "racks": { ... },
    "cabinets": { ... },
    "singleClawMachines": { ... },
    "doubleClawMachines": { ... },
    "keychainMachines": { ... }
  },
  "lastUpdated": "2025-01-18T..."
}
```

### GET /items
List items with quantities and optional forecasts.

**Query Parameters:**
- `locationType` - Filter by location (box_bin, rack, cabinet, single_claw_machine, double_claw_machine, keychain_machine)
- `category` - Filter by product category
- `includeForecasts` (default: false) - Include forecast data
- `limit` (default: 50, max: 100) - Results per page
- `offset` (default: 0) - Pagination offset

## Project Structure

```
bff-service/
├── src/
│   ├── index.ts              # Entry point
│   ├── app.ts                # Fastify app factory
│   ├── config/env.ts         # Environment validation
│   ├── plugins/              # Fastify plugins
│   │   ├── prisma.ts         # Database client
│   │   ├── auth.ts           # Supabase auth
│   │   ├── rate-limit.ts     # Rate limiting
│   │   └── error-handler.ts  # Error handling
│   ├── routes/               # API routes
│   ├── services/             # Business logic
│   ├── repositories/         # Data access
│   └── types/dto.ts          # Type definitions
├── prisma/schema.prisma      # Database schema
├── Dockerfile
└── package.json
```

## Troubleshooting

### "Can't reach database server"
- Ensure you're using the session pooler URL (port 5432), not transaction pooler (6543) or direct connection
- Check if your IP is allowed in Supabase network settings

### "Invalid or expired token"
- Tokens expire after 1 hour by default
- Get a fresh token from Supabase auth

### "Rate limit exceeded"
- Default: 100 requests per minute
- Wait for the TTL to reset or adjust `RATE_LIMIT_MAX` in environment
