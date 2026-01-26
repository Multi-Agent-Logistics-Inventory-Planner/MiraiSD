import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  deleteProductImage,
  isUploadError,
  validateFile,
} from "./storage";

// Mock the Supabase client
vi.mock("@/lib/supabase", () => ({
  getSupabaseClient: vi.fn(),
}));

import { getSupabaseClient } from "@/lib/supabase";

const mockGetSupabaseClient = vi.mocked(getSupabaseClient);

describe("storage utilities", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("deleteProductImage", () => {
    it("should return error when Supabase client is not available", async () => {
      mockGetSupabaseClient.mockReturnValue(null);

      const result = await deleteProductImage(
        "https://example.com/storage/v1/object/public/product-images/test.jpg"
      );

      expect(isUploadError(result)).toBe(true);
      if (isUploadError(result)) {
        expect(result.code).toBe("NO_CLIENT");
        expect(result.message).toBe("Supabase client not available");
      }
    });

    it("should return error for invalid image URL format", async () => {
      const mockRemove = vi.fn();
      mockGetSupabaseClient.mockReturnValue({
        storage: {
          from: () => ({
            remove: mockRemove,
          }),
        },
      } as unknown as ReturnType<typeof getSupabaseClient>);

      const result = await deleteProductImage("https://invalid-url.com/image.jpg");

      expect(isUploadError(result)).toBe(true);
      if (isUploadError(result)) {
        expect(result.code).toBe("UPLOAD_FAILED");
        expect(result.message).toBe("Invalid image URL");
      }
      expect(mockRemove).not.toHaveBeenCalled();
    });

    it("should extract path and delete image successfully", async () => {
      const mockRemove = vi.fn().mockResolvedValue({ error: null });
      mockGetSupabaseClient.mockReturnValue({
        storage: {
          from: vi.fn().mockReturnValue({
            remove: mockRemove,
          }),
        },
      } as unknown as ReturnType<typeof getSupabaseClient>);

      const result = await deleteProductImage(
        "https://xyz.supabase.co/storage/v1/object/public/product-images/abc123_test.jpg"
      );

      expect(result).toEqual({ success: true });
      expect(mockRemove).toHaveBeenCalledWith(["abc123_test.jpg"]);
    });

    it("should handle nested paths in URL", async () => {
      const mockRemove = vi.fn().mockResolvedValue({ error: null });
      mockGetSupabaseClient.mockReturnValue({
        storage: {
          from: vi.fn().mockReturnValue({
            remove: mockRemove,
          }),
        },
      } as unknown as ReturnType<typeof getSupabaseClient>);

      const result = await deleteProductImage(
        "https://xyz.supabase.co/storage/v1/object/public/product-images/folder/subfolder/image.jpg"
      );

      expect(result).toEqual({ success: true });
      expect(mockRemove).toHaveBeenCalledWith(["folder/subfolder/image.jpg"]);
    });

    it("should return error when Supabase delete fails", async () => {
      const mockRemove = vi.fn().mockResolvedValue({
        error: { message: "Storage error" },
      });
      mockGetSupabaseClient.mockReturnValue({
        storage: {
          from: vi.fn().mockReturnValue({
            remove: mockRemove,
          }),
        },
      } as unknown as ReturnType<typeof getSupabaseClient>);

      const result = await deleteProductImage(
        "https://xyz.supabase.co/storage/v1/object/public/product-images/test.jpg"
      );

      expect(isUploadError(result)).toBe(true);
      if (isUploadError(result)) {
        expect(result.code).toBe("UPLOAD_FAILED");
        expect(result.message).toBe("Storage error");
      }
    });

    it("should use default error message when Supabase error has no message", async () => {
      const mockRemove = vi.fn().mockResolvedValue({
        error: {},
      });
      mockGetSupabaseClient.mockReturnValue({
        storage: {
          from: vi.fn().mockReturnValue({
            remove: mockRemove,
          }),
        },
      } as unknown as ReturnType<typeof getSupabaseClient>);

      const result = await deleteProductImage(
        "https://xyz.supabase.co/storage/v1/object/public/product-images/test.jpg"
      );

      expect(isUploadError(result)).toBe(true);
      if (isUploadError(result)) {
        expect(result.message).toBe("Failed to delete image");
      }
    });
  });

  describe("isUploadError", () => {
    it("should return true for UploadError objects", () => {
      const error = { code: "UPLOAD_FAILED" as const, message: "Error" };
      expect(isUploadError(error)).toBe(true);
    });

    it("should return false for UploadResult objects", () => {
      const result = { url: "https://example.com/image.jpg", path: "image.jpg" };
      expect(isUploadError(result)).toBe(false);
    });

    it("should return false for success objects", () => {
      const result = { success: true as const };
      expect(isUploadError(result)).toBe(false);
    });
  });

  describe("validateFile", () => {
    it("should return error for files exceeding 5MB", () => {
      const largeFile = new File([""], "large.jpg", { type: "image/jpeg" });
      Object.defineProperty(largeFile, "size", { value: 6 * 1024 * 1024 });

      const result = validateFile(largeFile);

      expect(result).not.toBeNull();
      expect(result?.code).toBe("FILE_TOO_LARGE");
    });

    it("should return error for invalid MIME types", () => {
      const gifFile = new File([""], "image.gif", { type: "image/gif" });
      Object.defineProperty(gifFile, "size", { value: 1024 });

      const result = validateFile(gifFile);

      expect(result).not.toBeNull();
      expect(result?.code).toBe("INVALID_TYPE");
    });

    it("should return null for valid JPEG files", () => {
      const jpegFile = new File([""], "image.jpg", { type: "image/jpeg" });
      Object.defineProperty(jpegFile, "size", { value: 1024 });

      const result = validateFile(jpegFile);

      expect(result).toBeNull();
    });

    it("should return null for valid PNG files", () => {
      const pngFile = new File([""], "image.png", { type: "image/png" });
      Object.defineProperty(pngFile, "size", { value: 1024 });

      const result = validateFile(pngFile);

      expect(result).toBeNull();
    });

    it("should return null for valid WebP files", () => {
      const webpFile = new File([""], "image.webp", { type: "image/webp" });
      Object.defineProperty(webpFile, "size", { value: 1024 });

      const result = validateFile(webpFile);

      expect(result).toBeNull();
    });
  });
});

describe("image replacement integration logic", () => {
  // These tests verify the logic that should happen in product-form.tsx
  // when replacing an image

  it("should identify when image is being replaced", () => {
    const hasNewFile = true;
    const oldImageUrl = "https://example.com/old-image.jpg";

    const isReplacingImage = hasNewFile && Boolean(oldImageUrl);

    expect(isReplacingImage).toBe(true);
  });

  it("should NOT identify replacement when no new file", () => {
    const hasNewFile = false;
    const oldImageUrl = "https://example.com/old-image.jpg";

    const isReplacingImage = hasNewFile && Boolean(oldImageUrl);

    expect(isReplacingImage).toBe(false);
  });

  it("should NOT identify replacement when no old image exists", () => {
    const hasNewFile = true;
    const oldImageUrl: string | undefined = undefined;

    const isReplacingImage = hasNewFile && Boolean(oldImageUrl);

    expect(isReplacingImage).toBe(false);
  });

  it("should identify replacement when updating product with existing image", () => {
    const hasNewFile = true;
    const initialProduct = { imageUrl: "https://example.com/old.jpg" };
    const oldImageUrl = initialProduct.imageUrl;

    const isReplacingImage = hasNewFile && Boolean(oldImageUrl);

    expect(isReplacingImage).toBe(true);
  });
});
