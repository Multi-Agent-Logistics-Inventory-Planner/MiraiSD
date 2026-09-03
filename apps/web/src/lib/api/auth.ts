import { apiGet } from "./client";
import { User, UserRole } from "@/types/api";
import type { PermissionKey } from "@/lib/rbac";

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
  /**
   * Backend-resolved permission set for the session's role (RolePermissions.java).
   * Optional because a cached SessionResponse from before this field existed won't
   * have it - callers fall back to locally computing from role in that case.
   */
  permissions?: PermissionKey[];
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
