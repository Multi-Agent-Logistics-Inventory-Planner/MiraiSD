import { apiGet } from "./client";
import { User, UserRole } from "@/types/api";

/**
 * Combined session response from /api/auth/session endpoint.
 * Contains validation status AND user data in one response.
 */
export interface SessionResponse {
  valid: boolean;
  role?: UserRole;
  personId?: string;
  personName?: string;
  user?: User;
  message?: string;
}

/**
 * Get combined auth session (validation + user data) in a single call.
 * Reduces the N+1 pattern of separate validate + me calls.
 * @param token - The access token to validate
 */
export async function getAuthSession(token: string): Promise<SessionResponse> {
  return apiGet<SessionResponse>("/api/auth/session", {
    skipAuth: true,
    skipAuthRedirect: true,
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}
