package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.identity.api.UserMapper;
import com.mirai.inventoryservice.identity.api.UserResponseDTO;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.application.InvitationService;
import com.mirai.inventoryservice.identity.application.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtService jwtService;
    private final UserService userService;
    private final InvitationService invitationService;
    private final UserMapper userMapper;

    public AuthController(JwtService jwtService, UserService userService,
                         InvitationService invitationService, UserMapper userMapper) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.invitationService = invitationService;
        this.userMapper = userMapper;
    }

    /**
     * Validate the JWT token from frontend
     * Frontend will handle Supabase authentication and send JWT to backend
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7); // Remove "Bearer "

            if (jwtService.validateToken(token)) {
                String role = jwtService.extractRole(token);
                String personName = jwtService.extractName(token);
                String email = jwtService.extractEmail(token);
                UUID supabaseUserId = parseUuid(jwtService.extractPersonId(token));

                // Resolve the application user by sub (falling back to email for rows not yet
                // backfilled) - use database role for validation.
                String personId = null;
                Optional<User> resolved = userService.resolveBySupabaseIdOrEmail(supabaseUserId, email);
                if (resolved.isPresent()) {
                    User user = resolved.get();
                    personId = user.getId().toString();
                    // Use database role to ensure validation succeeds
                    role = user.getRole().name();
                }

                Map<String, Object> response = new HashMap<>();
                response.put("valid", true);
                response.put("role", role);
                response.put("personId", personId);
                response.put("personName", personName);

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Invalid token"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("valid", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Token validation failed"));
        }
    }

    /**
     * Combined session endpoint: validates token AND returns user data in one call.
     * Reduces the N+1 pattern of separate validate + me calls.
     * Returns validation status, role info, and full user object when available.
     */
    @GetMapping("/session")
    public ResponseEntity<?> getSession(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7); // Remove "Bearer "

            if (!jwtService.validateToken(token)) {
                return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Invalid token"));
            }

            String role = jwtService.extractRole(token);
            String personName = jwtService.extractName(token);
            String email = jwtService.extractEmail(token);
            UUID supabaseUserId = parseUuid(jwtService.extractPersonId(token));

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("role", role);
            response.put("personName", personName);
            response.put("personId", null);
            response.put("user", null);

            // Resolve user and include full user data in response
            Optional<User> resolved = userService.resolveBySupabaseIdOrEmail(supabaseUserId, email);
            if (resolved.isPresent()) {
                User user = resolved.get();
                response.put("personId", user.getId().toString());
                response.put("role", user.getRole().name()); // Override with DB role
                response.put("user", userMapper.toResponseDTO(user));
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("valid", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Session validation failed"));
        }
    }

    /**
     * Get current user profile from database.
     * Returns fresh user data (name, role, etc.) from the database.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();

        Optional<User> resolved = userService.resolveBySupabaseIdOrEmail(
                principal.supabaseUserId(), principal.email());
        if (resolved.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(userMapper.toResponseDTO(resolved.get()));
    }

    /**
     * Sync user from JWT claims after accepting invitation.
     * Creates local user record from Supabase JWT claims.
     */
    @PostMapping("/sync-user")
    public ResponseEntity<UserResponseDTO> syncUser(Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();

        String email = principal.email();
        String name = principal.personName();
        String role = principal.role();

        if (email == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<User> existing = userService.resolveBySupabaseIdOrEmail(principal.supabaseUserId(), email);
        if (existing.isPresent()) {
            return ResponseEntity.ok(userMapper.toResponseDTO(existing.get()));
        }

        User user = userService.createFromJwt(email, name, role, principal.supabaseUserId());

        invitationService.markInvitationAccepted(email);

        return ResponseEntity.ok(userMapper.toResponseDTO(user));
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