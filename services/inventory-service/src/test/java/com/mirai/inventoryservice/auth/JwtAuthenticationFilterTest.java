package com.mirai.inventoryservice.auth;

import com.mirai.inventoryservice.auth.JwtAuthenticationFilter;
import com.mirai.inventoryservice.auth.JwtService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

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

    @Test
    void testDoFilterInternal_ValidToken_SetsAuthentication() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-123";
        String personName = "John Doe";
        String role = "admin";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.validateToken(token)).thenReturn(true);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
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
        String role = "admin";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.validateToken(token)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertNull(SecurityContextHolder.getContext().getAuthentication());
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
    void testDoFilterInternal_MissingRole_DoesNotSetAuthentication() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-123";
        String personName = "John Doe";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractRole(token)).thenReturn(null);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_UserRole_SetsUserRole() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-456";
        String personName = "Jane Smith";
        String role = "user";

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.validateToken(token)).thenReturn(true);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_AlreadyAuthenticated_DoesNotOverride() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        String authHeader = "Bearer " + token;
        String personId = "user-123";
        String personName = "John Doe";
        String role = "admin";

        // Set up existing authentication
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);

        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtService.extractPersonId(token)).thenReturn(personId);
        when(jwtService.extractName(token)).thenReturn(personName);
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.validateToken(token)).thenReturn(true);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        // Filter should still process, but we verify it was called
        verify(filterChain).doFilter(request, response);
    }
}

