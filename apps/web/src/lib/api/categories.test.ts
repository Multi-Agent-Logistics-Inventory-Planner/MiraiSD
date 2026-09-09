import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();

vi.mock("@/lib/api/generated-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./generated-client")>();
  return {
    ...actual,
    webApiClient: { GET: (...args: unknown[]) => mockGet(...args) },
  };
});

import { getCatalogCategories } from "./categories";
import { GeneratedApiError } from "./generated-client";

function categoryFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: "cat-1",
    name: "Toys",
    slug: "toys",
    parentId: undefined,
    displayOrder: 0,
    isActive: true,
    usesPacks: false,
    children: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("getCatalogCategories", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls the global catalog v1 categories endpoint (no siteId - categories are wholly global)", async () => {
    mockGet.mockResolvedValue({
      data: [categoryFixture()],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getCatalogCategories();

    expect(mockGet).toHaveBeenCalledWith("/api/v1/catalog/categories", {});
    expect(result).toEqual([expect.objectContaining({ id: "cat-1", name: "Toys" })]);
  });

  it("recursively maps nested children, dropping only invalid nodes", async () => {
    mockGet.mockResolvedValue({
      data: [
        categoryFixture({
          id: "root-1",
          name: "Electronics",
          children: [
            categoryFixture({ id: "child-1", name: "Phones", parentId: "root-1" }),
            categoryFixture({ id: undefined, name: "Dropped" }),
          ],
        }),
      ],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getCatalogCategories();

    expect(result).toHaveLength(1);
    expect(result[0].children).toEqual([
      expect.objectContaining({ id: "child-1", name: "Phones" }),
    ]);
  });

  it("drops root records missing id or name instead of defaulting to an empty string", async () => {
    mockGet.mockResolvedValue({
      data: [categoryFixture(), categoryFixture({ id: undefined }), categoryFixture({ name: undefined })],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getCatalogCategories();

    expect(result).toHaveLength(1);
  });

  it("throws when the API returns an error", async () => {
    mockGet.mockResolvedValue({
      data: undefined,
      error: { message: "forbidden" },
      response: new Response(null, { status: 403 }),
    });

    await expect(getCatalogCategories()).rejects.toBeInstanceOf(GeneratedApiError);
  });
});
