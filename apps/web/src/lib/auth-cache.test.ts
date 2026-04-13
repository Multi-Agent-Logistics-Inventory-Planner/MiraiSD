import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  getCachedSession,
  setCachedSession,
  clearSessionCache,
  _testing,
} from "./auth-cache";
import type { SessionResponse } from "./api/auth";
import { UserRole } from "@/types/api";

describe("auth-cache", () => {
  const mockSession: SessionResponse = {
    valid: true,
    role: UserRole.ADMIN,
    personId: "user-123",
    personName: "Test User",
    user: {
      id: "user-123",
      email: "test@example.com",
      fullName: "Test User",
      role: UserRole.ADMIN,
      createdAt: "2024-01-01T00:00:00Z",
      updatedAt: "2024-01-01T00:00:00Z",
    },
  };

  beforeEach(() => {
    clearSessionCache();
    vi.useFakeTimers();
  });

  describe("getCachedSession", () => {
    it("should return null when cache is empty", () => {
      const result = getCachedSession("some-token");
      expect(result).toBeNull();
    });

    it("should return cached session when token matches", () => {
      setCachedSession("token-123", mockSession);
      const result = getCachedSession("token-123");
      expect(result).toEqual(mockSession);
    });

    it("should return null when token does not match", () => {
      setCachedSession("token-123", mockSession);
      const result = getCachedSession("different-token");
      expect(result).toBeNull();
    });

    it("should return null when cache has expired", () => {
      setCachedSession("token-123", mockSession);

      // Advance time past TTL (5 minutes + 1ms)
      vi.advanceTimersByTime(_testing.getCacheTTL() + 1);

      const result = getCachedSession("token-123");
      expect(result).toBeNull();
    });

    it("should return session when within TTL", () => {
      setCachedSession("token-123", mockSession);

      // Advance time but stay within TTL
      vi.advanceTimersByTime(_testing.getCacheTTL() - 1000);

      const result = getCachedSession("token-123");
      expect(result).toEqual(mockSession);
    });
  });

  describe("setCachedSession", () => {
    it("should store session with token and timestamp", () => {
      const now = Date.now();
      setCachedSession("token-123", mockSession);

      const cached = _testing.getCachedSessionRaw();
      expect(cached).not.toBeNull();
      expect(cached?.token).toBe("token-123");
      expect(cached?.response).toEqual(mockSession);
      expect(cached?.timestamp).toBeGreaterThanOrEqual(now);
    });

    it("should overwrite previous cache entry", () => {
      const session1: SessionResponse = { ...mockSession, personName: "User 1" };
      const session2: SessionResponse = { ...mockSession, personName: "User 2" };

      setCachedSession("token-1", session1);
      setCachedSession("token-2", session2);

      const result = getCachedSession("token-2");
      expect(result?.personName).toBe("User 2");

      // Old token should no longer work
      const oldResult = getCachedSession("token-1");
      expect(oldResult).toBeNull();
    });
  });

  describe("clearSessionCache", () => {
    it("should clear the cache", () => {
      setCachedSession("token-123", mockSession);
      expect(getCachedSession("token-123")).not.toBeNull();

      clearSessionCache();
      expect(getCachedSession("token-123")).toBeNull();
    });

    it("should be safe to call when cache is already empty", () => {
      expect(() => clearSessionCache()).not.toThrow();
    });
  });

  describe("cache TTL", () => {
    it("should have 5 minute TTL", () => {
      expect(_testing.getCacheTTL()).toBe(5 * 60 * 1000);
    });
  });
});
