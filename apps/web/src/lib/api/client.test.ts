import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { apiClient, apiPost } from "./client";

// Mock the Supabase client
vi.mock("@/lib/supabase", () => ({
  getSupabaseClient: () => ({
    auth: {
      getSession: () => Promise.resolve({ data: { session: { access_token: "test-token" } } }),
    },
  }),
}));

describe("apiClient", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  describe("empty response handling", () => {
    it("should handle 201 Created with empty body", async () => {
      // This simulates Spring Boot's ResponseEntity.status(CREATED).build()
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 201,
        headers: new Headers(), // No content-length header
        json: () => Promise.reject(new SyntaxError("Unexpected end of JSON input")),
      });

      // This should NOT throw - empty 201 responses should return undefined
      const result = await apiPost<void>("/api/stock-movements/transfer", {
        sourceLocationType: "BOX_BIN",
        sourceInventoryId: "123",
        destinationLocationType: "BOX_BIN",
        destinationLocationId: "456",
        quantity: 10,
        actorId: "user-1",
      });

      expect(result).toBeUndefined();
    });

    it("should handle 201 Created with content-length: 0", async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 201,
        headers: new Headers({ "content-length": "0" }),
        json: () => Promise.reject(new SyntaxError("Unexpected end of JSON input")),
      });

      const result = await apiPost<void>("/api/test", { data: "test" });

      expect(result).toBeUndefined();
    });

    it("should handle 204 No Content", async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
        headers: new Headers(),
        json: () => Promise.reject(new SyntaxError("Unexpected end of JSON input")),
      });

      const result = await apiClient<void>("/api/test");

      expect(result).toBeUndefined();
    });

    it("should parse JSON for 200 OK with body", async () => {
      const responseData = { id: "123", message: "success" };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve(responseData),
      });

      const result = await apiClient<typeof responseData>("/api/test");

      expect(result).toEqual(responseData);
    });

    it("should parse JSON for 201 Created with body", async () => {
      const responseData = { id: "456", created: true };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 201,
        headers: new Headers({ "content-type": "application/json", "content-length": "30" }),
        json: () => Promise.resolve(responseData),
      });

      const result = await apiClient<typeof responseData>("/api/test");

      expect(result).toEqual(responseData);
    });
  });
});
