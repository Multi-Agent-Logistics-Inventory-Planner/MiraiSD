import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();

vi.mock("@/lib/api/generated-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./generated-client")>();
  return {
    ...actual,
    webApiClient: { GET: (...args: unknown[]) => mockGet(...args) },
  };
});

import { getSiteProducts } from "./products";
import { GeneratedApiError } from "./generated-client";

const SITE_ID = "site-1";

function siteProductFixture(overrides: Record<string, unknown> = {}) {
  return {
    productId: "p-1",
    sku: "WID-1",
    name: "Widget",
    isStocked: true,
    forecastingEnabled: true,
    unitCost: 10,
    msrp: 20,
    reorderPoint: 5,
    targetStockLevel: 50,
    leadTimeDays: 7,
    version: 1,
    ...overrides,
  };
}

describe("getSiteProducts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls the site-scoped products endpoint", async () => {
    mockGet.mockResolvedValue({
      data: [siteProductFixture()],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getSiteProducts(SITE_ID);

    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/sites/{siteId}/products",
      expect.objectContaining({ params: { path: { siteId: SITE_ID } } })
    );
    expect(result).toEqual([expect.objectContaining({ productId: "p-1", isStocked: true, msrp: 20 })]);
  });

  it("maps a null/absent version to null, not undefined or 0 (AC-2: absent row has no version to compare)", async () => {
    mockGet.mockResolvedValue({
      data: [siteProductFixture({ isStocked: false, version: undefined })],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getSiteProducts(SITE_ID);

    expect(result[0].version).toBeNull();
  });

  it("drops records missing productId instead of defaulting to an empty string", async () => {
    mockGet.mockResolvedValue({
      data: [siteProductFixture(), { ...siteProductFixture(), productId: undefined }],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getSiteProducts(SITE_ID);

    expect(result).toHaveLength(1);
  });

  it("throws when the API returns an error", async () => {
    mockGet.mockResolvedValue({
      data: undefined,
      error: { message: "forbidden" },
      response: new Response(null, { status: 403 }),
    });

    await expect(getSiteProducts(SITE_ID)).rejects.toBeInstanceOf(GeneratedApiError);
  });
});
