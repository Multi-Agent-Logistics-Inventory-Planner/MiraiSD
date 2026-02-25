import { apiGet, apiPost } from "./client";
import { AuthValidationResponse, User } from "@/types/api";

/**
 * Validate Supabase JWT token with backend
 * Returns user role and info if valid
 */
export async function validateToken(
  token: string
): Promise<AuthValidationResponse> {
  return apiPost<AuthValidationResponse>("/api/auth/validate", undefined, {
    skipAuth: true,
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/**
 * Get current user profile from database
 * Returns fresh user data (name, role, etc.)
 * @param token - Optional access token to use (avoids calling getSession during auth state changes)
 */
export async function getCurrentUser(token?: string): Promise<User> {
  // skipAuthRedirect: true to allow fallback to JWT data if this fails
  // If token is provided, use it directly to avoid getSession() calls during auth callbacks
  if (token) {
    return apiGet<User>("/api/auth/me", {
      skipAuth: true,
      skipAuthRedirect: true,
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
  }
  return apiGet<User>("/api/auth/me", { skipAuthRedirect: true });
}
