import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { deleteProductImage, isUploadError, validateFile } from "./images";

const URL_FIXTURE = "https://img.mirai-inventory.com/abc_test.webp";

function mockFetchOnce(response: Partial<Response> & { ok: boolean }) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue(""),
      json: vi.fn().mockResolvedValue({}),
      status: 200,
      ...response,
    }),
  );
}

describe("deleteProductImage", () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => vi.unstubAllGlobals());

  it("posts the url to /api/images/delete and returns success", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, text: async () => "" });
    vi.stubGlobal("fetch", fetchMock);

    const result = await deleteProductImage(URL_FIXTURE);

    expect(result).toEqual({ success: true });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/images/delete",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: URL_FIXTURE }),
      }),
    );
  });

  it("returns UPLOAD_FAILED with the server message when the response is not ok", async () => {
    mockFetchOnce({ ok: false, status: 500, text: async () => "boom" });

    const result = await deleteProductImage(URL_FIXTURE);

    expect(isUploadError(result)).toBe(true);
    if (isUploadError(result)) {
      expect(result.code).toBe("UPLOAD_FAILED");
      expect(result.message).toBe("boom");
    }
  });

  it("falls back to a status-coded message when the body is empty", async () => {
    mockFetchOnce({ ok: false, status: 404, text: async () => "" });

    const result = await deleteProductImage(URL_FIXTURE);

    expect(isUploadError(result)).toBe(true);
    if (isUploadError(result)) {
      expect(result.message).toBe("Delete failed (404)");
    }
  });
});

describe("isUploadError", () => {
  it("returns true for UploadError objects", () => {
    expect(isUploadError({ code: "UPLOAD_FAILED", message: "x" })).toBe(true);
  });

  it("returns false for UploadResult objects", () => {
    expect(isUploadError({ url: "https://x", path: "x" })).toBe(false);
  });

  it("returns false for { success: true }", () => {
    expect(isUploadError({ success: true })).toBe(false);
  });
});

describe("validateFile", () => {
  function fakeFile(type: string, size: number): File {
    const file = new File([""], "x", { type });
    Object.defineProperty(file, "size", { value: size });
    return file;
  }

  it("rejects files larger than 5MB", () => {
    const result = validateFile(fakeFile("image/jpeg", 6 * 1024 * 1024));
    expect(result?.code).toBe("FILE_TOO_LARGE");
  });

  it("rejects unsupported MIME types", () => {
    const result = validateFile(fakeFile("image/gif", 1024));
    expect(result?.code).toBe("INVALID_TYPE");
  });

  it.each(["image/jpeg", "image/png", "image/webp"])(
    "accepts %s under the size limit",
    (mime) => {
      expect(validateFile(fakeFile(mime, 1024))).toBeNull();
    },
  );
});
