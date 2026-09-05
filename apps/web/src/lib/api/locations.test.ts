import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();

vi.mock("@/lib/api/generated-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./generated-client")>();
  return {
    ...actual,
    webApiClient: { GET: (...args: unknown[]) => mockGet(...args) },
  };
});

import { getSiteStorageLocations } from "./locations";
import { GeneratedApiError } from "./generated-client";

const SITE_ID = "site-1";

function storageLocationFixture() {
  return {
    id: "sl-1",
    code: "BOX_BINS",
    name: "Box Bins",
    hasDisplay: false,
    isDisplayOnly: false,
    displayOrder: 0,
  };
}

describe("getSiteStorageLocations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls the site-scoped storage-locations endpoint", async () => {
    mockGet.mockResolvedValue({
      data: [storageLocationFixture()],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getSiteStorageLocations(SITE_ID);

    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/sites/{siteId}/storage-locations",
      expect.objectContaining({ params: { path: { siteId: SITE_ID } } })
    );
    expect(result).toEqual([expect.objectContaining({ code: "BOX_BINS" })]);
  });

  it("throws when the API returns an error", async () => {
    mockGet.mockResolvedValue({
      data: undefined,
      error: { message: "not found" },
      response: new Response(null, { status: 404 }),
    });

    await expect(getSiteStorageLocations(SITE_ID)).rejects.toBeInstanceOf(GeneratedApiError);
  });

  it("throws when the response is not ok even with no parsed error body", async () => {
    mockGet.mockResolvedValue({
      data: undefined,
      error: undefined,
      response: new Response(null, { status: 502 }),
    });

    await expect(getSiteStorageLocations(SITE_ID)).rejects.toBeInstanceOf(GeneratedApiError);
  });

  it("drops records missing id or code instead of defaulting to an empty string", async () => {
    mockGet.mockResolvedValue({
      data: [storageLocationFixture(), { ...storageLocationFixture(), id: undefined }],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getSiteStorageLocations(SITE_ID);

    expect(result).toHaveLength(1);
  });
});
