import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();

vi.mock("./client", () => ({
  apiGet: (...args: unknown[]) => mockGet(...args),
}));

import { getSiteLastAudits, getSiteUsers } from "./site-users";

describe("site-users.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue([]);
  });

  it("lists members under the site path", async () => {
    await getSiteUsers("site-1");
    expect(mockGet).toHaveBeenCalledWith("/api/v1/sites/site-1/users");
  });

  it("reads last audit times under the site path", async () => {
    mockGet.mockResolvedValue({ "u-1": "2026-10-01T00:00:00Z" });
    const result = await getSiteLastAudits("site-1");
    expect(mockGet).toHaveBeenCalledWith("/api/v1/sites/site-1/users/last-audits");
    expect(result).toEqual({ "u-1": "2026-10-01T00:00:00Z" });
  });
});
