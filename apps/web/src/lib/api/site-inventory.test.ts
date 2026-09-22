import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockDelete = vi.fn();

vi.mock("@/lib/api/generated-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./generated-client")>();
  return {
    ...actual,
    webApiClient: {
      GET: (...args: unknown[]) => mockGet(...args),
      POST: (...args: unknown[]) => mockPost(...args),
      DELETE: (...args: unknown[]) => mockDelete(...args),
    },
  };
});

import {
  getSiteInventoryTotals,
  getSiteLocationInventory,
  getSiteProductInventory,
  getSiteMovements,
  adjustSiteInventory,
  transferSiteInventory,
  batchTransferSiteInventory,
  createSiteLocationInventory,
  deleteSiteLocationInventory,
  newIdempotencyKey,
} from "./site-inventory";
import { GeneratedApiError } from "./generated-client";
import { LocationType, StockMovementReason } from "@/types/api";

const SITE_ID = "site-1";

function ok<T>(data: T) {
  return { data, error: undefined, response: new Response(null, { status: 200 }) };
}

describe("site-inventory.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("newIdempotencyKey", () => {
    it("generates a fresh, distinct key on every call", () => {
      const a = newIdempotencyKey();
      const b = newIdempotencyKey();
      expect(a).not.toEqual(b);
      expect(a).toMatch(/^[0-9a-f-]{36}$/i);
    });
  });

  describe("getSiteInventoryTotals", () => {
    it("calls the totals route and drops rows missing productId", async () => {
      mockGet.mockResolvedValue(
        ok([
          { productId: "p-1", totalQuantity: 5, lastUpdatedAt: "2026-01-01T00:00:00Z" },
          { totalQuantity: 9 },
        ])
      );

      const result = await getSiteInventoryTotals(SITE_ID);

      expect(mockGet).toHaveBeenCalledWith(
        "/api/v1/sites/{siteId}/inventory/totals",
        expect.objectContaining({ params: { path: { siteId: SITE_ID }, query: undefined } })
      );
      expect(result).toEqual([
        { productId: "p-1", totalQuantity: 5, lastUpdatedAt: "2026-01-01T00:00:00Z" },
      ]);
    });

    it("passes productIds through as a query filter when given", async () => {
      mockGet.mockResolvedValue(ok([]));

      await getSiteInventoryTotals(SITE_ID, ["p-1", "p-2"]);

      expect(mockGet).toHaveBeenCalledWith(
        "/api/v1/sites/{siteId}/inventory/totals",
        expect.objectContaining({
          params: { path: { siteId: SITE_ID }, query: { productIds: ["p-1", "p-2"] } },
        })
      );
    });

    it("a zero-stock product still reports totalQuantity: 0, never dropped or undefined", async () => {
      mockGet.mockResolvedValue(ok([{ productId: "p-1", totalQuantity: 0 }]));

      const result = await getSiteInventoryTotals(SITE_ID);

      expect(result).toEqual([{ productId: "p-1", totalQuantity: 0, lastUpdatedAt: undefined }]);
    });
  });

  describe("getSiteLocationInventory", () => {
    it("calls the per-location route and drops rows missing identity", async () => {
      mockGet.mockResolvedValue(
        ok([
          { inventoryId: "inv-1", productId: "p-1", quantity: 3, updatedAt: "2026-01-01T00:00:00Z" },
          { inventoryId: "inv-2" }, // missing productId
        ])
      );

      const result = await getSiteLocationInventory(SITE_ID, "loc-1");

      expect(mockGet).toHaveBeenCalledWith(
        "/api/v1/sites/{siteId}/inventory/locations/{locationId}",
        expect.objectContaining({ params: { path: { siteId: SITE_ID, locationId: "loc-1" } } })
      );
      expect(result).toEqual([
        { inventoryId: "inv-1", productId: "p-1", quantity: 3, updatedAt: "2026-01-01T00:00:00Z" },
      ]);
    });

    it("throws GeneratedApiError on failure", async () => {
      mockGet.mockResolvedValue({
        data: undefined,
        error: { message: "nope" },
        response: new Response(null, { status: 404 }),
      });

      await expect(getSiteLocationInventory(SITE_ID, "loc-1")).rejects.toBeInstanceOf(
        GeneratedApiError
      );
    });
  });

  describe("getSiteProductInventory", () => {
    it("maps entries and falls back to safe defaults", async () => {
      mockGet.mockResolvedValue(
        ok({
          productId: "p-1",
          productSku: "SKU-1",
          productName: "Widget",
          totalQuantity: 7,
          entries: [
            {
              inventoryId: "inv-1",
              locationType: "RACK",
              locationId: "loc-1",
              locationCode: "R01",
              locationLabel: "Rack 1",
              quantity: 7,
              updatedAt: "2026-01-01T00:00:00Z",
            },
          ],
        })
      );

      const result = await getSiteProductInventory(SITE_ID, "p-1");

      expect(result.totalQuantity).toBe(7);
      expect(result.entries).toHaveLength(1);
      expect(result.entries[0]).toMatchObject({ inventoryId: "inv-1", locationType: "RACK" });
    });

    it("throws GeneratedApiError, not a raw TypeError, on an ok-but-empty body (review finding 8)", async () => {
      // unwrapGeneratedResponse returns `undefined` for a 200/204 with no parsed body - this
      // must not fall through to an unguarded `data.productId` dereference.
      mockGet.mockResolvedValue(ok(undefined));

      await expect(getSiteProductInventory(SITE_ID, "p-1")).rejects.toBeInstanceOf(
        GeneratedApiError
      );
    });
  });

  describe("getSiteMovements", () => {
    it("serializes pageable as flat page/size query params, not pageable[page] (matches Spring's Pageable binding)", async () => {
      mockGet.mockResolvedValue(
        ok({
          content: [],
          totalElements: 0,
          totalPages: 0,
          size: 20,
          number: 0,
          first: true,
          last: true,
        })
      );

      await getSiteMovements(SITE_ID, { itemId: "p-1" }, 2, 10);

      expect(mockGet).toHaveBeenCalledTimes(1);
      const [, options] = mockGet.mock.calls[0];
      const serializer = options.querySerializer;
      expect(typeof serializer).toBe("function");

      const qs = serializer({ itemId: "p-1", pageable: { page: 2, size: 10 } });
      const params = new URLSearchParams(qs);
      expect(params.get("page")).toBe("2");
      expect(params.get("size")).toBe("10");
      expect(params.get("itemId")).toBe("p-1");
      // Never the openapi-fetch default deepObject shape.
      expect(qs).not.toContain("pageable[page]");
      expect(qs).not.toContain("pageable%5Bpage%5D");
    });

    it("labels UNKNOWN-site rows, never silently hides them (T-6d-10)", async () => {
      mockGet.mockResolvedValue(
        ok({
          content: [
            {
              id: 1,
              itemId: "p-1",
              locationType: "RACK",
              quantityChange: 5,
              reason: "RESTOCK",
              at: "2026-01-01T00:00:00Z",
              siteAttribution: "UNKNOWN",
            },
            {
              id: 2,
              itemId: "p-1",
              locationType: "RACK",
              quantityChange: -1,
              reason: "SALE",
              at: "2026-01-02T00:00:00Z",
            },
          ],
          totalElements: 2,
          totalPages: 1,
          size: 20,
          number: 0,
          first: true,
          last: true,
        })
      );

      const result = await getSiteMovements(SITE_ID);

      expect(result.content[0].siteAttribution).toBe("UNKNOWN");
      expect(result.content[1].siteAttribution).toBeUndefined();
    });

    it("throws GeneratedApiError, not a raw TypeError, on an ok-but-empty body (review finding 8)", async () => {
      mockGet.mockResolvedValue(ok(undefined));

      await expect(getSiteMovements(SITE_ID)).rejects.toBeInstanceOf(GeneratedApiError);
    });
  });

  describe("mutations", () => {
    it("adjustSiteInventory sends the Idempotency-Key header and no actorId field", async () => {
      mockPost.mockResolvedValue(ok(undefined));

      await adjustSiteInventory(SITE_ID, "key-1", {
        locationType: LocationType.RACK,
        locationId: "loc-1",
        adjustments: [{ inventoryId: "inv-1", quantityChange: -2 }],
        reason: StockMovementReason.SALE,
      });

      expect(mockPost).toHaveBeenCalledWith(
        "/api/v1/sites/{siteId}/inventory/adjustments",
        expect.objectContaining({
          params: { path: { siteId: SITE_ID }, header: { "Idempotency-Key": "key-1" } },
        })
      );
      const [, options] = mockPost.mock.calls[0];
      expect(options.body).not.toHaveProperty("actorId");
    });

    it("transferSiteInventory posts the transfer without actorId", async () => {
      mockPost.mockResolvedValue(ok(undefined));

      await transferSiteInventory(SITE_ID, "key-2", {
        sourceLocationType: LocationType.RACK,
        sourceInventoryId: "inv-1",
        destinationLocationType: LocationType.SHELF,
        destinationLocationId: "loc-2",
        quantity: 3,
      });

      expect(mockPost).toHaveBeenCalledWith(
        "/api/v1/sites/{siteId}/inventory/transfers",
        expect.objectContaining({
          params: { path: { siteId: SITE_ID }, header: { "Idempotency-Key": "key-2" } },
        })
      );
    });

    it("batchTransferSiteInventory wraps every transfer under one idempotency key", async () => {
      mockPost.mockResolvedValue(ok(undefined));

      await batchTransferSiteInventory(SITE_ID, "key-3", [
        {
          sourceLocationType: LocationType.RACK,
          sourceInventoryId: "inv-1",
          destinationLocationType: LocationType.SHELF,
          destinationLocationId: "loc-2",
          quantity: 1,
        },
        {
          sourceLocationType: LocationType.RACK,
          sourceInventoryId: "inv-2",
          destinationLocationType: LocationType.SHELF,
          destinationLocationId: "loc-2",
          quantity: 2,
        },
      ]);

      const [, options] = mockPost.mock.calls[0];
      expect(options.body.transfers).toHaveLength(2);
      expect(options.params.header["Idempotency-Key"]).toBe("key-3");
    });

    it("createSiteLocationInventory returns the created entry", async () => {
      mockPost.mockResolvedValue(
        ok({ inventoryId: "inv-9", productId: "p-1", quantity: 4, updatedAt: "2026-01-01T00:00:00Z" })
      );

      const result = await createSiteLocationInventory(SITE_ID, "loc-1", "key-4", {
        productId: "p-1",
        quantity: 4,
      });

      expect(result).toEqual({
        inventoryId: "inv-9",
        productId: "p-1",
        quantity: 4,
        updatedAt: "2026-01-01T00:00:00Z",
      });
    });

    it("createSiteLocationInventory throws when the response is missing identity", async () => {
      mockPost.mockResolvedValue(ok({ quantity: 4 }));

      await expect(
        createSiteLocationInventory(SITE_ID, "loc-1", "key-5", { productId: "p-1", quantity: 4 })
      ).rejects.toThrow(/missing identity/i);
    });

    it("deleteSiteLocationInventory calls DELETE with the Idempotency-Key header", async () => {
      mockDelete.mockResolvedValue(ok(undefined));

      await deleteSiteLocationInventory(SITE_ID, "loc-1", "inv-1", "key-6");

      expect(mockDelete).toHaveBeenCalledWith(
        "/api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}",
        expect.objectContaining({
          params: {
            path: { siteId: SITE_ID, locationId: "loc-1", inventoryId: "inv-1" },
            header: { "Idempotency-Key": "key-6" },
          },
        })
      );
    });
  });
});
