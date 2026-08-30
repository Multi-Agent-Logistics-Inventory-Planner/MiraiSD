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
import java.util.UUID;

/**
 * Assigns a correlation ID to every request: reuses one supplied by the caller, or generates
 * a new one. Stored in MDC for the lifetime of the request so all log lines carry it, and
 * echoed back as a response header. Cleared in a finally block since MDC is thread-local and
 * this thread will be reused by the servlet container for unrelated requests.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationIdContext.HEADER_NAME);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationIdContext.MDC_KEY, correlationId);
        response.setHeader(CorrelationIdContext.HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdContext.MDC_KEY);
        }
    }
}
