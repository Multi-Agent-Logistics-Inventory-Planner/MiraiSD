package com.mirai.inventoryservice.identity.domain;

import java.util.UUID;

/**
 * Immutable authenticated-request principal, per
 * docs/specs/authentication-and-authorization.md section 4: Spring Security must expose an
 * immutable principal containing the backend user ID and Supabase subject, and must not place a
 * client-provided actor ID or role into trusted request state.
 */
public record AuthenticatedPrincipal(
        UUID supabaseUserId,
        UUID backendUserId,
        String email,
        String personName,
        String role) {
}
