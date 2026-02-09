package com.mirai.inventoryservice.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for RateLimitingFilter.
 * Tests the filter logic in isolation without Spring context.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filter;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        // Create filter with limit of 5 for faster tests
        filter = new RateLimitingFilter(5);
        filter.clearBuckets();

        responseWriter = new StringWriter();
        // Use lenient stubbing since not all tests trigger rate limit response
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("Should allow requests below rate limit")
    void shouldAllowRequestsBelowLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        // Make requests up to the limit
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Verify filter chain was called for all requests
        verify(filterChain, times(5)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should block requests exceeding rate limit")
    void shouldBlockRequestsExceedingLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getRemoteAddr()).thenReturn("192.168.1.2");

        // Exhaust the limit
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Next request should be blocked
        filter.doFilterInternal(request, response, filterChain);

        // Verify response status was set to 429
        verify(response).setStatus(429);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("Too many requests");
    }

    @Test
    @DisplayName("Should exclude health endpoint from rate limiting")
    void shouldExcludeHealthEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/health");
        // No need to mock getRemoteAddr - health endpoint doesn't check IP

        // Make many requests to health endpoint
        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // All requests should pass through
        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should use X-Forwarded-For header when present")
    void shouldUseXForwardedForHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        // Make requests using forwarded header
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Next request should be blocked (same forwarded IP)
        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("Should extract first IP from comma-separated X-Forwarded-For")
    void shouldExtractFirstIpFromXForwardedFor() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        // Multiple IPs in header (client, proxy1, proxy2)
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2, 10.0.0.3");

        // Exhaust limit
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Should be blocked based on first IP
        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("Should use remoteAddr when X-Forwarded-For is empty")
    void shouldUseRemoteAddrWhenNoForwardedHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        // Exhaust limit using remote address
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("Should track multiple IPs independently")
    void shouldTrackMultipleIpsIndependently() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");

        // Exhaust limit for IP1
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // IP2 should still have full quota
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.2");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Both IPs exhausted their limits
        assertThat(filter.getTrackedIpCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should clear buckets properly")
    void shouldClearBuckets() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getRemoteAddr()).thenReturn("192.168.1.50");

        // Make some requests
        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        assertThat(filter.getTrackedIpCount()).isEqualTo(1);

        // Clear buckets
        filter.clearBuckets();

        assertThat(filter.getTrackedIpCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle empty X-Forwarded-For string")
    void shouldHandleEmptyXForwardedFor() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.60");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should set correct content type on rate limit response")
    void shouldSetCorrectContentType() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getRemoteAddr()).thenReturn("192.168.1.70");

        // Exhaust limit
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setContentType("application/json");
    }
}
