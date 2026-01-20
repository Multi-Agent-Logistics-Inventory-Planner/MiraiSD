package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.dtos.mappers.UserMapper;
import com.mirai.inventoryservice.dtos.responses.UserResponseDTO;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.services.InvitationService;
import com.mirai.inventoryservice.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
                String personId = jwtService.extractPersonId(token);
                String personName = jwtService.extractName(token);

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
     * Sync user from JWT claims after accepting invitation.
     * Creates local user record from Supabase JWT claims.
     */
    @PostMapping("/sync-user")
    public ResponseEntity<UserResponseDTO> syncUser(Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, String> principal = (Map<String, String>) authentication.getPrincipal();

        String email = principal.get("email");
        String name = principal.get("personName");
        String role = principal.get("role");

        if (email == null) {
            return ResponseEntity.badRequest().build();
        }

        if (userService.existsByEmail(email)) {
            User existingUser = userService.getUserByEmail(email);
            return ResponseEntity.ok(userMapper.toResponseDTO(existingUser));
        }

        User user = userService.createFromJwt(email, name, role);

        invitationService.markInvitationAccepted(email);

        return ResponseEntity.ok(userMapper.toResponseDTO(user));
    }

}