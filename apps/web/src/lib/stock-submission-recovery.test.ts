import { beforeEach, describe, expect, it } from "vitest";
import { clearUncertainStockSubmission, readUncertainStockSubmission, storeUncertainStockSubmission } from "./stock-submission-recovery";

describe("stock submission recovery", () => {
  beforeEach(() => sessionStorage.clear());

  it("keeps an uncertain adjustment bound to its original site and idempotency key", () => {
    const record = { version: 2 as const, createdAt: Date.now(), userId: "user-a", siteId: "site-a", idempotencyKey: "original-key", kind: "adjust" as const, payload: { locationType: "RACK" as never, locationId: "loc-a", adjustments: [], reason: "ADJUSTMENT" as never }, productIds: ["p-1"] };
    storeUncertainStockSubmission(record);
    expect(readUncertainStockSubmission("user-a", "site-b", "adjust")).toBeNull();
    expect(readUncertainStockSubmission("user-b", "site-a", "adjust")).toBeNull();
    expect(readUncertainStockSubmission("user-a", "site-a", "adjust")).toMatchObject({ idempotencyKey: "original-key", siteId: "site-a" });
    clearUncertainStockSubmission(record);
    expect(readUncertainStockSubmission("user-a", "site-a", "adjust")).toBeNull();
  });

  it("expires recovery records after one day instead of replaying old stock intent", () => {
    storeUncertainStockSubmission({ version: 2, createdAt: Date.now() - 24 * 60 * 60 * 1000 - 1, userId: "user-a", siteId: "site-a", idempotencyKey: "old-key", kind: "transfer", payload: { sourceLocationType: "RACK" as never, sourceInventoryId: "inv", destinationLocationType: "RACK" as never, quantity: 1 }, productId: "p-1" });
    expect(readUncertainStockSubmission("user-a", "site-a", "transfer")).toBeNull();
  });

  it("keeps different operations instead of overwriting an unresolved submission", () => {
    storeUncertainStockSubmission({ version: 2, createdAt: Date.now(), userId: "user-a", siteId: "site-a", idempotencyKey: "adjust-key", kind: "adjust", payload: { locationType: "RACK" as never, locationId: "loc-a", adjustments: [], reason: "ADJUSTMENT" as never }, productIds: ["p-1"] });
    storeUncertainStockSubmission({ version: 2, createdAt: Date.now(), userId: "user-a", siteId: "site-a", idempotencyKey: "initial-key", kind: "initial-stock", productId: "p-2", locationId: "loc-a", quantity: 1 });
    expect(readUncertainStockSubmission("user-a", "site-a", "adjust")?.idempotencyKey).toBe("adjust-key");
    expect(readUncertainStockSubmission("user-a", "site-a", "initial-stock")?.idempotencyKey).toBe("initial-key");
  });
});
