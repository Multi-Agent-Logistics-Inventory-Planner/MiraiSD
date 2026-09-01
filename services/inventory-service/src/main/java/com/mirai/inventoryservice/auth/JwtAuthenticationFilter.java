package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.application.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;
        String personId = null;
        String personName = null;
        String personEmail = null;

        // Extract JWT token from Authorization header
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                personId = jwtService.extractPersonId(token);
                personName = jwtService.extractName(token);
                personEmail = jwtService.extractEmail(token);
            } catch (Exception e) {
                // Invalid token
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Validate token and set authentication (role can be null for unauthenticated users)
        if (personId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtService.validateToken(token)) {
                // Authority MUST come from the backend-controlled User record, never from the
                // JWT's user_metadata.role claim: Supabase user_metadata is editable by the user
                // themselves via the client SDK, so trusting it here would let any authenticated
                // user grant themselves ADMIN. See docs/specs/authentication-and-authorization.md
                // sections 1 and 3 ("No user-editable JWT claim may grant backend authority" /
                // "user_metadata.role MUST be ignored").
                //
                // A fresh per-request lookup is used rather than a cache so a role change or
                // deactivation takes effect on the very next request, matching section 6
                // ("security correctness must not depend on a client refreshing its token").
                // If this lookup becomes a measured hot path, any cache added here MUST use a
                // bounded TTL with explicit invalidation on role change, per the same section.
                //
                // Resolution prefers the JWT sub (supabaseUserId) over email, per section 2 -
                // email is a profile attribute, not a stable authorization identifier. Existing
                // rows without a captured sub yet are matched by email and lazily backfilled by
                // resolveBySupabaseIdOrEmail, so every user is migrated onto sub-based lookup on
                // their first request after this ships.
                UUID supabaseUserId = parseUuid(personId);
                Optional<User> resolved = userService.resolveBySupabaseIdOrEmail(supabaseUserId, personEmail);
                String dbRole = resolved.map(u -> u.getRole().name()).orElse(null);
                UUID backendUserId = resolved.map(User::getId).orElse(null);

                AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                        supabaseUserId,
                        backendUserId,
                        personEmail,
                        personName != null ? personName : "Unknown",
                        dbRole);

                // No matching backend user record grants no elevated role, never a
                // JWT-claimed one.
                String role = dbRole != null ? dbRole.toUpperCase() : "USER";
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                    );

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
