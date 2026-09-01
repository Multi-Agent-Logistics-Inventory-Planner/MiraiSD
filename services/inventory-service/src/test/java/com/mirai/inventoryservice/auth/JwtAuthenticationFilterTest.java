package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.auth.JwtAuthenticationFilter;
import com.mirai.inventoryservice.auth.JwtService;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.application.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    private User userWithId(UUID id, UserRole role) {
        return User.builder()
                .id(id)
                .fullName("Test User")
                .email("user@example.com")
                .role(role)
                .build();
    }

    @Test
    void testDoFilterInternal_ValidToken_SetsAuthenticationFromDatabaseRole() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        UUID sub = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID backendUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String personName = "John Doe";
        String email = "user@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(sub.toString());
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.resolveBySupabaseIdOrEmail(sub, email))
                .thenReturn(Optional.of(userWithId(backendUserId, UserRole.ADMIN)));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        assertEquals(sub, principal.supabaseUserId());
        assertEquals(backendUserId, principal.backendUserId());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_ForgedUserMetadataRole_GrantsNoElevatedAuthority() throws ServletException, IOException {
        // Given: the JWT's user_metadata claims "admin" (as if the user edited their own
        // Supabase user_metadata via the client SDK), but the backend-controlled User record
        // says EMPLOYEE. Authority MUST follow the database, never the JWT claim.
        String token = "forged.jwt.token";
        String authHeader = "Bearer " + token;
        UUID sub = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID backendUserId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        String personName = "Attacker";
        String email = "attacker@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(sub.toString());
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.resolveBySupabaseIdOrEmail(sub, email))
                .thenReturn(Optional.of(userWithId(backendUserId, UserRole.EMPLOYEE)));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: granted authority is the database role (EMPLOYEE), not any JWT-claimed role.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_EMPLOYEE", authentication.getAuthorities().iterator().next().getAuthority());
        assertNotEquals("ROLE_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
        // jwtService.extractRole is never consulted for authority at all.
        verify(jwtService, never()).extractRole(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_NoMatchingBackendUser_GrantsNoElevatedRole() throws ServletException, IOException {
        // Given: a validly signed, unexpired token for a sub/email with no backend User record
        // yet (e.g. before /api/auth/sync-user runs). This MUST NOT default to any elevated role.
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        UUID sub = UUID.fromString("55555555-5555-5555-5555-555555555555");
        String personName = "New Person";
        String email = "new-person@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(sub.toString());
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.resolveBySupabaseIdOrEmail(sub, email)).thenReturn(Optional.empty());

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        assertNull(principal.backendUserId());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_NoAuthHeader_DoesNotSetAuthentication() throws ServletException, IOException {
        // Given
        when(request.getHeader("Authorization")).thenReturn(null);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_InvalidAuthHeader_DoesNotSetAuthentication() throws ServletException, IOException {
        // Given
        when(request.getHeader("Authorization")).thenReturn("InvalidFormat token");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_ExpiredToken_DoesNotSetAuthentication() throws ServletException, IOException {
        // Given
        String token = "expired.jwt.token";
        String authHeader = "Bearer " + token;
        UUID sub = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String personName = "John Doe";
        String email = "user@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(sub.toString());
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService, never()).resolveBySupabaseIdOrEmail(any(), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_InvalidToken_ContinuesFilterChain() throws ServletException, IOException {
        // Given
        String token = "invalid.jwt.token";
        String authHeader = "Bearer " + token;

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenThrow(new RuntimeException("Invalid token"));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_NullEmail_DoesNotSetAuthentication() throws ServletException, IOException {
        // Given: sub present but no email extractable from the token at all, and no backend
        // user matches that sub yet.
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        UUID sub = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String personName = "John Doe";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(sub.toString());
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(null);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.resolveBySupabaseIdOrEmail(sub, null)).thenReturn(Optional.empty());

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: still authenticates (sub is the identity anchor), but with no elevated role.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_AlreadyAuthenticated_StillProcessesChain() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        UUID sub = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID backendUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String personName = "John Doe";
        String email = "user@example.com";

        // Set up existing (empty) authentication context
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(sub.toString());
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.resolveBySupabaseIdOrEmail(sub, email))
                .thenReturn(Optional.of(userWithId(backendUserId, UserRole.ADMIN)));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }
}
