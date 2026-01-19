import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Mock env config BEFORE any imports
vi.mock('../src/config/env.js', () => ({
  env: {
    DATABASE_URL: 'postgresql://user:pass@localhost:5432/postgres',
    AUTH_DISABLED: true,
    SUPABASE_URL: 'http://localhost',
    SUPABASE_ANON_KEY: 'test-anon',
    SUPABASE_JWT_SECRET: 'test-jwt-secret',
    PORT: 5001,
  },
}));

// Mock prisma with proper return shapes
vi.mock('../src/lib/prisma.js', () => ({
  prisma: {
    products: {
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
    },
    forecast_predictions: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
    },
    box_bin_inventory: { aggregate: vi.fn().mockResolvedValue({ _count: { id: 0 }, _sum: { quantity: 0 } }) },
    rack_inventory: { aggregate: vi.fn().mockResolvedValue({ _count: { id: 0 }, _sum: { quantity: 0 } }) },
    cabinet_inventory: { aggregate: vi.fn().mockResolvedValue({ _count: { id: 0 }, _sum: { quantity: 0 } }) },
    single_claw_machine_inventory: { aggregate: vi.fn().mockResolvedValue({ _count: { id: 0 }, _sum: { quantity: 0 } }) },
    double_claw_machine_inventory: { aggregate: vi.fn().mockResolvedValue({ _count: { id: 0 }, _sum: { quantity: 0 } }) },
    keychain_machine_inventory: { aggregate: vi.fn().mockResolvedValue({ _count: { id: 0 }, _sum: { quantity: 0 } }) },
  },
}));

// Mock supabase
vi.mock('../src/lib/supabase.js', () => ({
  supabase: {
    auth: {
      getUser: vi.fn().mockResolvedValue({
        data: { user: { id: 'user-1', email: 'user@example.com' } },
        error: null,
      }),
    },
  },
}));

import { buildApp } from '../src/app.js';

describe('dashboard-api routes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('GET /health returns ok', async () => {
    const app = await buildApp();
    const res = await app.inject({ method: 'GET', url: '/health' });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.status).toBe('ok');
    expect(body.service).toBe('dashboard-api');
  });

  it('GET /items returns paginated response', async () => {
    const app = await buildApp();
    const res = await app.inject({ method: 'GET', url: '/items?limit=10' });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toHaveProperty('data');
    expect(body).toHaveProperty('pagination');
  });

  it('GET /forecasts/:itemId returns 404 when not found', async () => {
    const app = await buildApp();
    const res = await app.inject({ method: 'GET', url: '/forecasts/00000000-0000-0000-0000-000000000000' });
    await app.close();

    expect(res.statusCode).toBe(404);
    const body = res.json();
    expect(body.status).toBe(404);
    expect(body.error).toBe('Not Found');
  });

  it('GET /forecasts/at-risk returns paginated response', async () => {
    const app = await buildApp();
    const res = await app.inject({ method: 'GET', url: '/forecasts/at-risk?threshold=7' });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toHaveProperty('data');
    expect(body).toHaveProperty('pagination');
  });

  it('GET /inventory/summary returns summary', async () => {
    const app = await buildApp();
    const res = await app.inject({ method: 'GET', url: '/inventory/summary' });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toHaveProperty('totalItems');
    expect(body).toHaveProperty('totalQuantity');
    expect(body).toHaveProperty('byLocation');
  });
});
