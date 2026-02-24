import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// We need to test the reportError function behavior in different environments
// Since we cannot directly modify process.env.NODE_ENV in tests,
// we'll use vi.stubEnv to stub environment variables

describe("reportError", () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.resetModules();
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
    vi.unstubAllEnvs();
  });

  describe("in development mode", () => {
    beforeEach(() => {
      vi.stubEnv("NODE_ENV", "development");
    });

    it("should log error to console with context", async () => {
      const { reportError } = await import("../error-reporting");
      const testError = new Error("Test error");

      reportError("Route error", testError);

      expect(consoleErrorSpy).toHaveBeenCalledTimes(1);
      expect(consoleErrorSpy).toHaveBeenCalledWith("Route error:", testError);
    });

    it("should handle errors with digest property", async () => {
      const { reportError } = await import("../error-reporting");
      const errorWithDigest = Object.assign(new Error("Digest error"), {
        digest: "abc123",
      });

      reportError("Global error", errorWithDigest);

      expect(consoleErrorSpy).toHaveBeenCalledTimes(1);
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        "Global error:",
        errorWithDigest
      );
    });
  });

  describe("in production mode", () => {
    beforeEach(() => {
      vi.stubEnv("NODE_ENV", "production");
    });

    it("should NOT log error to console", async () => {
      const { reportError } = await import("../error-reporting");
      const testError = new Error("Production error");

      reportError("Route error", testError);

      expect(consoleErrorSpy).not.toHaveBeenCalled();
    });

    it("should NOT log error with digest to console", async () => {
      const { reportError } = await import("../error-reporting");
      const errorWithDigest = Object.assign(new Error("Production digest"), {
        digest: "xyz789",
      });

      reportError("Global error", errorWithDigest);

      expect(consoleErrorSpy).not.toHaveBeenCalled();
    });
  });

  describe("in test mode", () => {
    beforeEach(() => {
      vi.stubEnv("NODE_ENV", "test");
    });

    it("should log error to console (same as development)", async () => {
      const { reportError } = await import("../error-reporting");
      const testError = new Error("Test mode error");

      reportError("Test context", testError);

      expect(consoleErrorSpy).toHaveBeenCalledTimes(1);
      expect(consoleErrorSpy).toHaveBeenCalledWith("Test context:", testError);
    });
  });
});
