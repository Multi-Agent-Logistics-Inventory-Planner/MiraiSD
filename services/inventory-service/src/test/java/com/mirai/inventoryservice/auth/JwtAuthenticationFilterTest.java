package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.auth.JwtAuthenticationFilter;
import com.mirai.inventoryservice.auth.JwtService;
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

    private User userWithRole(UserRole role) {
        return User.builder()
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
        String personId = "user-123";
        String personName = "John Doe";
        String email = "user@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.existsByEmail(email)).thenReturn(true);
        when(userService.getUserByEmail(email)).thenReturn(userWithRole(UserRole.ADMIN));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_ForgedUserMetadataRole_GrantsNoElevatedAuthority() throws ServletException, IOException {
        // Given: the JWT's user_metadata claims "admin" (as if the user edited their own
        // Supabase user_metadata via the client SDK), but the backend-controlled User record
        // says EMPLOYEE. Authority MUST follow the database, never the JWT claim.
        String token = "forged.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-456";
        String personName = "Attacker";
        String email = "attacker@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.existsByEmail(email)).thenReturn(true);
        when(userService.getUserByEmail(email)).thenReturn(userWithRole(UserRole.EMPLOYEE));

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
        // Given: a validly signed, unexpired token for an email with no backend User record yet
        // (e.g. before /api/auth/sync-user runs). This MUST NOT default to any elevated role.
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-789";
        String personName = "New Person";
        String email = "new-person@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.existsByEmail(email)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
        verify(userService, never()).getUserByEmail(anyString());
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
        String personId = "user-123";
        String personName = "John Doe";
        String email = "user@example.com";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService, never()).existsByEmail(anyString());
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
        // Given: personId present but no email extractable from the token at all.
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-123";
        String personName = "John Doe";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(null);
        when(jwtService.validateToken(token)).thenReturn(true);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: still authenticates (personId is the identity anchor), but with no elevated role.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
        verify(userService, never()).existsByEmail(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_AlreadyAuthenticated_StillProcessesChain() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-123";
        String personName = "John Doe";
        String email = "user@example.com";

        // Set up existing (empty) authentication context
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(userService.existsByEmail(email)).thenReturn(true);
        when(userService.getUserByEmail(email)).thenReturn(userWithRole(UserRole.ADMIN));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }
}
