# Dashboard API

REST API for the Mirai inventory dashboard. Provides endpoints for forecasts, inventory summaries, and item data.

## Prerequisites

- Node.js 20+
- Access to Supabase project

## Quick Start

```bash
# Install dependencies
npm install

# Generate Prisma client
npm run prisma:generate

# Start development server
npm run dev
```

Server runs at `http://localhost:5001`

## Configuration

Copy `env.example` to `.env` and configure:

```env
DATABASE_URL=postgresql://...          # Supabase connection string
SUPABASE_URL=https://xxx.supabase.co   # Supabase project URL
SUPABASE_ANON_KEY=eyJ...               # Supabase anon key
SUPABASE_JWT_SECRET=...                # JWT secret (for auth)
AUTH_DISABLED=true                     # Set to true for local dev (skips auth)
PORT=5001                              # Server port
```

## API Endpoints

### Health Check
```bash
curl http://localhost:5001/health
```

### Forecasts
```bash
# Get forecast for specific item
curl http://localhost:5001/forecasts/:itemId

# Get at-risk items
curl "http://localhost:5001/forecasts/at-risk?threshold=7&limit=10"
```

### Inventory
```bash
# Get inventory summary
curl http://localhost:5001/inventory/summary
```

### Items
```bash
# List items
curl "http://localhost:5001/items?limit=10"

# Filter by location
curl "http://localhost:5001/items?locationType=box_bin"

# Include forecasts
curl "http://localhost:5001/items?includeForecasts=true"
```

## Authentication

Endpoints require a Supabase access token (except `/health`).

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:5001/items
```

For local development, set `AUTH_DISABLED=true` to skip auth checks.

## Project Structure

```
dashboard-api/
├── src/
│   ├── config/env.ts       # Environment config
│   ├── lib/
│   │   ├── prisma.ts       # Database client
│   │   └── supabase.ts     # Supabase client
│   ├── routes/
│   │   ├── forecasts.ts    # Forecast endpoints
│   │   ├── inventory.ts    # Inventory endpoints
│   │   ├── items.ts        # Item endpoints
│   │   └── health.ts       # Health check
│   ├── types.ts            # TypeScript types
│   ├── app.ts              # Fastify app
│   └── index.ts            # Entry point
├── tests/                  # Test files
├── prisma/schema.prisma    # Database schema
└── package.json
```

## Scripts

```bash
npm run dev          # Start dev server with hot reload
npm run build        # Build TypeScript
npm run start        # Start production server
npm test             # Run tests
npm run prisma:generate  # Generate Prisma client
npm run prisma:pull      # Pull schema from database
```

## Docker

From the repo root:
```bash
docker-compose -f infra/docker-compose.yml build dashboard-api
docker-compose -f infra/docker-compose.yml up dashboard-api
```
