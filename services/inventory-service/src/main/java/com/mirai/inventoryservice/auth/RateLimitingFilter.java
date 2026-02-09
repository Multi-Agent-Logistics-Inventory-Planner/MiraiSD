package com.mirai.inventoryservice.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using Bucket4j token bucket algorithm.
 *
 * Features:
 * - Configurable requests per minute via environment variable
 * - Per-IP rate limiting using ConcurrentHashMap
 * - Supports X-Forwarded-For header for reverse proxy deployments
 * - Excludes /health endpoint from rate limiting
 * - Returns HTTP 429 (Too Many Requests) when limit exceeded
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public RateLimitingFilter(
            @Value("${rate.limit.requests.per.minute:100}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
        log.info("Rate limiting initialized: {} requests per minute per IP", requestsPerMinute);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // Exclude health endpoint from rate limiting (critical for monitoring)
        if (isHealthEndpoint(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        Bucket bucket = getBucketForIp(clientIp);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, requestUri);
            sendRateLimitExceededResponse(response);
        }
    }

    /**
     * Check if the request is for the health endpoint.
     *
     * @param requestUri The request URI
     * @return true if this is the health endpoint
     */
    private boolean isHealthEndpoint(String requestUri) {
        return "/health".equals(requestUri);
    }

    /**
     * Extract the client IP address from the request.
     * Handles X-Forwarded-For header for reverse proxy deployments.
     *
     * @param request The HTTP request
     * @return The client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs: client, proxy1, proxy2
            // The first IP is the original client
            String firstIp = xForwardedFor.split(",")[0].trim();
            return firstIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Get or create a rate limit bucket for the given IP address.
     *
     * @param clientIp The client IP address
     * @return The bucket for this IP
     */
    private Bucket getBucketForIp(String clientIp) {
        return bucketCache.computeIfAbsent(clientIp, ip -> createNewBucket());
    }

    /**
     * Create a new rate limit bucket with the configured limit.
     *
     * @return A new bucket configured with the rate limit
     */
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Send the rate limit exceeded response (HTTP 429).
     *
     * @param response The HTTP response
     * @throws IOException if writing the response fails
     */
    private void sendRateLimitExceededResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429); // HTTP 429 Too Many Requests
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
    }

    /**
     * Clear all rate limit buckets.
     * Primarily used for testing to ensure test isolation.
     */
    public void clearBuckets() {
        bucketCache.clear();
    }

    /**
     * Get the current number of tracked IPs.
     * Useful for monitoring and debugging.
     *
     * @return The number of IPs being tracked
     */
    public int getTrackedIpCount() {
        return bucketCache.size();
    }
}
