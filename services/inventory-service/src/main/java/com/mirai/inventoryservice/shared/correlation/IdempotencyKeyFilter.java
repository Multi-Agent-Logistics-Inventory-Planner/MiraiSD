package com.mirai.inventoryservice.shared.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Carries the client-supplied {@code Idempotency-Key} header into {@link IdempotencyKeyContext}
 * for the lifetime of the request (.specs/phase-6-inventory T-6c-10/T-6c-12), mirroring
 * {@link CorrelationIdFilter} exactly. Does not enforce the header's presence -- it is only
 * required on the v1 mutation routes (Q-6c-3), which enforce that themselves via
 * {@code @RequestHeader(required = true)}; every other route simply carries no key, exactly as
 * before this filter existed. Cleared in a finally block since MDC is thread-local and this
 * thread will be reused by the servlet container for unrelated requests.
 */
@Component
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IdempotencyKeyContext.HEADER_NAME);
        if (StringUtils.hasText(idempotencyKey)) {
            MDC.put(IdempotencyKeyContext.MDC_KEY, idempotencyKey);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(IdempotencyKeyContext.MDC_KEY);
        }
    }
}
