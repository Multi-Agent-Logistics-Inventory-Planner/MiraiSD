import { apiPost } from "./client";
import { AuthValidationResponse } from "@/types/api";

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
