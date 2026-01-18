import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

function setBaseEnv() {
  // env.ts is evaluated at import-time, so make sure these exist before importing buildApp/auth plugin.
  process.env.DATABASE_URL = process.env.DATABASE_URL ?? 'postgresql://user:pass@localhost:5432/postgres';
  process.env.PORT = process.env.PORT ?? '5001';
  process.env.RATE_LIMIT_MAX = process.env.RATE_LIMIT_MAX ?? '100';
  process.env.RATE_LIMIT_WINDOW = process.env.RATE_LIMIT_WINDOW ?? '60000';
}

function makeMockPrisma(overrides: Record<string, any> = {}) {
  return {
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
    ...overrides,
  };
}

async function buildTestApp(opts: {
  prisma?: any;
  authDisabled?: boolean;
  supabaseUser?: { id: string; email?: string | null } | null;
  supabaseError?: any;
} = {}) {
  const authDisabled = opts.authDisabled ?? true;
  setBaseEnv();

  // Reset module graph so mocked modules apply cleanly per test.
  vi.resetModules();

  // Hard-mock env so tests don't depend on local `.env` and so AUTH_DISABLED is deterministic.
  vi.doMock('../config/env.js', () => ({
    env: {
      DATABASE_URL: 'postgresql://user:pass@localhost:5432/postgres',
      AUTH_DISABLED: authDisabled,
      SUPABASE_URL: 'http://localhost',
      SUPABASE_ANON_KEY: 'test-anon',
      SUPABASE_JWT_SECRET: 'test-jwt-secret',
      RATE_LIMIT_MAX: 100,
      RATE_LIMIT_WINDOW: 60000,
      PORT: 5001,
    },
  }));

  // Mock Supabase client for auth-on tests (no network).
  vi.doMock('@supabase/supabase-js', () => {
    return {
      createClient: () => ({
        auth: {
          getUser: vi.fn().mockResolvedValue({
            data: { user: opts.supabaseUser ?? { id: 'user-1', email: 'user@example.com' } },
            error: opts.supabaseError ?? null,
          }),
        },
      }),
    };
  });

  const { buildApp } = await import('../app.js');
  const app = await buildApp({
    registerPrisma: false,
    prisma: opts.prisma ?? makeMockPrisma(),
  });
  return app;
}

describe('dashboard-api routes (unit via inject)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(async () => {
    // Nothing global to close here; each test closes its own app.
  });

  it('GET /health returns ok without auth', async () => {
    const app = await buildTestApp({ authDisabled: true });
    const res = await app.inject({ method: 'GET', url: '/health' });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.status).toBe('ok');
    expect(body.service).toBe('dashboard-api');
    expect(typeof body.timestamp).toBe('string');
  });

  it('GET /items returns 401 when auth enabled and missing token', async () => {
    const app = await buildTestApp({ authDisabled: false });
    const res = await app.inject({ method: 'GET', url: '/items?limit=1' });
    await app.close();

    expect(res.statusCode).toBe(401);
    const body = res.json();
    expect(body).toMatchObject({
      status: 401,
      error: 'Unauthorized',
      message: 'Missing or invalid authorization header',
    });
    expect(typeof body.timestamp).toBe('string');
  });

  it('GET /items succeeds with Bearer token when auth enabled (supabase mocked)', async () => {
    const prisma = makeMockPrisma({
      products: {
        findMany: vi.fn().mockResolvedValue([]),
        count: vi.fn().mockResolvedValue(0),
      },
    });
    const app = await buildTestApp({ authDisabled: false, prisma });
    const res = await app.inject({
      method: 'GET',
      url: '/items?limit=1&offset=0',
      headers: { authorization: 'Bearer test-token' },
    });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toEqual({
      data: [],
      pagination: { total: 0, limit: 1, offset: 0, hasMore: false },
    });
  });

  it('GET /items returns 500 with consistent error envelope when DB/repo throws', async () => {
    const prisma = makeMockPrisma({
      products: {
        findMany: vi.fn().mockRejectedValue(new Error('db down')),
        count: vi.fn().mockResolvedValue(0),
      },
    });
    const app = await buildTestApp({ authDisabled: true, prisma });
    const res = await app.inject({ method: 'GET', url: '/items?limit=1&offset=0' });
    await app.close();

    expect(res.statusCode).toBe(500);
    const body = res.json();
    expect(body).toMatchObject({
      status: 500,
      error: 'Internal Server Error',
      message: 'An unexpected error occurred',
    });
    expect(typeof body.timestamp).toBe('string');
  });

  it('GET /forecasts/:itemId returns 404 when forecast not found', async () => {
    const prisma = makeMockPrisma({
      forecast_predictions: {
        findFirst: vi.fn().mockResolvedValue(null),
      },
    });
    const app = await buildTestApp({ authDisabled: true, prisma });
    const res = await app.inject({ method: 'GET', url: '/forecasts/00000000-0000-0000-0000-000000000000' });
    await app.close();

    expect(res.statusCode).toBe(404);
    const body = res.json();
    expect(body).toMatchObject({
      status: 404,
      error: 'Not Found',
    });
    expect(typeof body.timestamp).toBe('string');
    expect(typeof body.message).toBe('string');
  });

  it('GET /forecasts/at-risk returns paginated response', async () => {
    const prisma = makeMockPrisma({
      forecast_predictions: {
        findMany: vi.fn().mockResolvedValue([]),
        count: vi.fn().mockResolvedValue(0),
      },
    });
    const app = await buildTestApp({ authDisabled: true, prisma });
    const res = await app.inject({ method: 'GET', url: '/forecasts/at-risk?threshold=7&limit=2&offset=0' });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toEqual({
      data: [],
      pagination: { total: 0, limit: 2, offset: 0, hasMore: false },
    });
  });

  it('GET /inventory/summary returns summary shape', async () => {
    const prisma = makeMockPrisma({
      forecast_predictions: {
        count: vi.fn().mockResolvedValue(0),
      },
    });
    const app = await buildTestApp({ authDisabled: true, prisma });
    const res = await app.inject({ method: 'GET', url: '/inventory/summary' });
    await app.close();

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toHaveProperty('totalItems');
    expect(body).toHaveProperty('totalQuantity');
    expect(body).toHaveProperty('atRiskCount');
    expect(body).toHaveProperty('criticalCount');
    expect(body).toHaveProperty('byLocation.boxBins');
    expect(body).toHaveProperty('lastUpdated');
  });

  it('GET /inventory/summary returns 500 with consistent error envelope when DB/repo throws', async () => {
    const prisma = makeMockPrisma({
      box_bin_inventory: { aggregate: vi.fn().mockRejectedValue(new Error('db down')) },
    });
    const app = await buildTestApp({ authDisabled: true, prisma });
    const res = await app.inject({ method: 'GET', url: '/inventory/summary' });
    await app.close();

    expect(res.statusCode).toBe(500);
    const body = res.json();
    expect(body).toMatchObject({
      status: 500,
      error: 'Internal Server Error',
      message: 'An unexpected error occurred',
    });
    expect(typeof body.timestamp).toBe('string');
  });
});

