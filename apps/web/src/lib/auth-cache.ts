import type { SessionResponse } from "./api/auth";

interface CachedSession {
  token: string;
  response: SessionResponse;
  timestamp: number;
}

const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

let cachedSession: CachedSession | null = null;

/**
 * Get cached session response for a given token.
 * Returns null if cache miss, token mismatch, or TTL expired.
 */
export function getCachedSession(token: string): SessionResponse | null {
  if (!cachedSession) return null;
  if (cachedSession.token !== token) return null;
  if (Date.now() - cachedSession.timestamp > CACHE_TTL_MS) {
    cachedSession = null;
    return null;
  }
  return cachedSession.response;
}

/**
 * Cache a session response for a given token.
 */
export function setCachedSession(
  token: string,
  response: SessionResponse
): void {
  cachedSession = {
    token,
    response,
    timestamp: Date.now(),
  };
}

/**
 * Clear the session cache.
 * Called on sign-out or when session validation fails.
 */
export function clearSessionCache(): void {
  cachedSession = null;
}

// Export for testing
export const _testing = {
  getCacheTTL: () => CACHE_TTL_MS,
  getCachedSessionRaw: () => cachedSession,
};
