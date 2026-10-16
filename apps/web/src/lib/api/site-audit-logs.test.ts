import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();

vi.mock("./client", () => ({
  apiGet: (...args: unknown[]) => mockGet(...args),
}));

import { getSiteAuditLogDetail, getSiteAuditLogs } from "./site-audit-logs";
import { StockMovementReason } from "@/types/api";

const SITE_ID = "site-1";
const BASE = `/api/v1/sites/${SITE_ID}/audit-logs`;

describe("site-audit-logs.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({});
  });

  it("lists audit logs under the site path with paging defaults", async () => {
    await getSiteAuditLogs(SITE_ID);
    expect(mockGet).toHaveBeenCalledWith(`${BASE}?page=0&size=20`);
  });

  it("forwards every filter, repeating reasons", async () => {
    await getSiteAuditLogs(
      SITE_ID,
      {
        search: "x",
        actorId: "a-1",
        reason: StockMovementReason.ADJUSTMENT,
        reasons: [StockMovementReason.KUJI_PRIZE_WON, StockMovementReason.KUJI_DRAW_REVERSED],
        fromDate: "2026-09-01",
        toDate: "2026-09-30",
        productId: "p-1",
        locationId: "l-1",
      },
      2,
      50
    );
    const url = mockGet.mock.calls[0][0] as string;
    expect(url.startsWith(`${BASE}?`)).toBe(true);
    const params = new URLSearchParams(url.split("?")[1]);
    expect(params.get("page")).toBe("2");
    expect(params.get("size")).toBe("50");
    expect(params.get("search")).toBe("x");
    expect(params.get("actorId")).toBe("a-1");
    expect(params.get("reason")).toBe("ADJUSTMENT");
    expect(params.getAll("reasons")).toEqual(["KUJI_PRIZE_WON", "KUJI_DRAW_REVERSED"]);
    expect(params.get("fromDate")).toBe("2026-09-01");
    expect(params.get("toDate")).toBe("2026-09-30");
    expect(params.get("productId")).toBe("p-1");
    expect(params.get("locationId")).toBe("l-1");
  });

  it("reads detail under the site path", async () => {
    await getSiteAuditLogDetail(SITE_ID, "log-1");
    expect(mockGet).toHaveBeenCalledWith(`${BASE}/log-1`);
  });
});
