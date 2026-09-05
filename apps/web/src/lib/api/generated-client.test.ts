import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetAuthToken = vi.fn();
const mockRedirectToLogin = vi.fn();

vi.mock("@/lib/api/auth-token", () => ({
  getAuthToken: () => mockGetAuthToken(),
  redirectToLogin: () => mockRedirectToLogin(),
}));

vi.mock("@/lib/api/backend-url", () => ({
  BACKEND_BASE_URL: "https://api.test",
}));

import { createWebApiClient, unwrapGeneratedResponse, GeneratedApiError } from "./generated-client";

describe("createWebApiClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("attaches the Authorization header from getAuthToken", async () => {
    mockGetAuthToken.mockResolvedValue("test-token");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "u1" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      })
    );

    const client = createWebApiClient({ fetch: fetchMock });
    await client.GET("/api/v1/me", {});

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.headers.get("Authorization")).toBe("Bearer test-token");
  });

  it("calls onUnauthorized when the server responds 401", async () => {
    mockGetAuthToken.mockResolvedValue(null);
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Unauthorized" }), {
        status: 401,
        headers: { "content-type": "application/json" },
      })
    );

    const client = createWebApiClient({ fetch: fetchMock });
    await client.GET("/api/v1/me", {});

    expect(mockRedirectToLogin).toHaveBeenCalledTimes(1);
  });

  it("does not attach an Authorization header when there is no token", async () => {
    mockGetAuthToken.mockResolvedValue(null);
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "u1" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      })
    );

    const client = createWebApiClient({ fetch: fetchMock });
    await client.GET("/api/v1/me", {});

    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.headers.has("Authorization")).toBe(false);
  });
});

describe("unwrapGeneratedResponse", () => {
  it("returns data on a successful response", () => {
    const result = unwrapGeneratedResponse({
      data: { id: "1" },
      error: undefined,
      response: new Response(null, { status: 200 }),
    });

    expect(result).toEqual({ id: "1" });
  });

  it("throws a real Error (with the parsed message) when the API returns an error body", () => {
    expect(() =>
      unwrapGeneratedResponse({
        data: undefined,
        error: { message: "not found" },
        response: new Response(null, { status: 404 }),
      })
    ).toThrow(GeneratedApiError);

    try {
      unwrapGeneratedResponse({
        data: undefined,
        error: { message: "not found" },
        response: new Response(null, { status: 404 }),
      });
    } catch (e) {
      expect(e).toBeInstanceOf(Error);
      expect((e as GeneratedApiError).message).toBe("not found");
      expect((e as GeneratedApiError).status).toBe(404);
    }
  });

  it("throws even when error is undefined, if the response is not ok (empty-body failure)", () => {
    // openapi-fetch returns { error: undefined } for a non-ok response with an empty body
    // (204/HEAD/Content-Length: 0) - a 502/504 from a proxy, or an empty 403, must not be
    // read as success just because there was nothing to parse.
    expect(() =>
      unwrapGeneratedResponse({
        data: undefined,
        error: undefined,
        response: new Response(null, { status: 502 }),
      })
    ).toThrow(GeneratedApiError);
  });

  it("does not throw on an ok response with no data (e.g. 204)", () => {
    const result = unwrapGeneratedResponse({
      data: undefined,
      error: undefined,
      response: new Response(null, { status: 204 }),
    });

    expect(result).toBeUndefined();
  });
});
