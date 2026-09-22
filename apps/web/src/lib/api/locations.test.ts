import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();
const mockDelete = vi.fn();

vi.mock("@/lib/api/generated-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./generated-client")>();
  return {
    ...actual,
    webApiClient: { GET: (...args: unknown[]) => mockGet(...args), POST: (...args: unknown[]) => mockPost(...args), PUT: (...args: unknown[]) => mockPut(...args), DELETE: (...args: unknown[]) => mockDelete(...args) },
  };
});

import { getSiteStorageLocations, getSiteLocations, resolveSiteLocationId, createSiteLocation, updateSiteLocation, deleteSiteLocation } from "./locations";
import { GeneratedApiError } from "./generated-client";
import { LocationType } from "@/types/api";

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

// Flat SiteLocationDTO shape (.specs/phase-6-inventory 6e, T-6e-be-7 - SiteLocationController
// now returns SiteLocationDTO, not the raw Location entity's nested storageLocation).
function siteLocationFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: "loc-na-1",
    locationCode: "NA",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    storageLocationId: "sl-na",
    storageLocationCode: "NOT_ASSIGNED",
    ...overrides,
  };
}

describe("getSiteLocations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls the site-scoped locations route with the storageLocation filter", async () => {
    mockGet.mockResolvedValue({
      data: [siteLocationFixture()],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getSiteLocations(SITE_ID, "NOT_ASSIGNED");

    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/sites/{siteId}/locations",
      expect.objectContaining({
        params: { path: { siteId: SITE_ID }, query: { storageLocation: "NOT_ASSIGNED" } },
      })
    );
    expect(result).toEqual([
      expect.objectContaining({
        id: "loc-na-1",
        locationCode: "NA",
        storageLocationId: "sl-na",
        storageLocationType: "NOT_ASSIGNED",
      }),
    ]);
  });

  it("drops rows missing identity (id, locationCode, or storageLocationId)", async () => {
    mockGet.mockResolvedValue({
      data: [siteLocationFixture(), siteLocationFixture({ id: undefined })],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await getSiteLocations(SITE_ID);

    expect(result).toHaveLength(1);
  });
});

describe("resolveSiteLocationId", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("passes a real locationId through unchanged for a non-NOT_ASSIGNED type", async () => {
    const result = await resolveSiteLocationId(SITE_ID, LocationType.RACK, "loc-real-1");

    expect(result).toBe("loc-real-1");
    expect(mockGet).not.toHaveBeenCalled();
  });

  it("resolves the NOT_ASSIGNED virtual ID to the site's real NA location", async () => {
    mockGet.mockResolvedValue({
      data: [siteLocationFixture()],
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    const result = await resolveSiteLocationId(
      SITE_ID,
      LocationType.NOT_ASSIGNED,
      "__not_assigned__"
    );

    expect(result).toBe("loc-na-1");
    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/sites/{siteId}/locations",
      expect.objectContaining({
        params: { path: { siteId: SITE_ID }, query: { storageLocation: "NOT_ASSIGNED" } },
      })
    );
  });

  it("throws when the site has no NOT_ASSIGNED location at all, rather than silently resolving to undefined", async () => {
    mockGet.mockResolvedValue({ data: [], error: undefined, response: new Response(null, { status: 200 }) });

    await expect(
      resolveSiteLocationId(SITE_ID, LocationType.NOT_ASSIGNED, "__not_assigned__")
    ).rejects.toThrow(/not found/i);
  });
});


describe("site location CRUD", () => {
  beforeEach(() => vi.clearAllMocks());
  it("creates through the generated v1 route and maps its flat DTO", async () => {
    mockPost.mockResolvedValue({ data: siteLocationFixture(), response: new Response(null, { status: 201 }) });
    const body = { storageLocationId: "storage-second", locationCode: "B2" };
    const result = await createSiteLocation("second", body);
    expect(mockPost).toHaveBeenCalledWith("/api/v1/sites/{siteId}/locations", { params: { path: { siteId: "second" } }, body });
    expect(result.id).toBe(siteLocationFixture().id);
    expect(result.storageLocationType).toBe("NOT_ASSIGNED");
  });
  it("renames through the generated v1 route", async () => {
    mockPut.mockResolvedValue({ data: siteLocationFixture(), response: new Response(null, { status: 200 }) });
    await updateSiteLocation("second", "location-second", { locationCode: "B2" });
    expect(mockPut).toHaveBeenCalledWith("/api/v1/sites/{siteId}/locations/{id}", { params: { path: { siteId: "second", id: "location-second" } }, body: { locationCode: "B2" } });
  });
  it("accepts a successful empty DELETE, but rejects empty error responses", async () => {
    mockDelete.mockResolvedValueOnce({ response: new Response(null, { status: 204 }) });
    await expect(deleteSiteLocation("second", "loc")).resolves.toBeUndefined();
    expect(mockDelete).toHaveBeenCalledWith("/api/v1/sites/{siteId}/locations/{id}", { params: { path: { siteId: "second", id: "loc" } } });
    mockDelete.mockResolvedValueOnce({ response: new Response(null, { status: 403 }) });
    await expect(deleteSiteLocation("second", "loc")).rejects.toMatchObject({ status: 403 });
  });
  it("rejects malformed write responses instead of reporting success", async () => {
    mockPost.mockResolvedValue({ data: {}, response: new Response(null, { status: 201 }) });
    await expect(createSiteLocation("second", { storageLocationId: "storage", locationCode: "B2" })).rejects.toThrow();
    mockPut.mockResolvedValue({ response: new Response(null, { status: 403 }) });
    await expect(updateSiteLocation("second", "loc", { locationCode: "B2" })).rejects.toMatchObject({ status: 403 });
  });
});
