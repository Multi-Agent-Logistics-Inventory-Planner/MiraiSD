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
 */
export async function getCurrentUser(): Promise<User> {
  return apiGet<User>("/api/auth/me");
}
