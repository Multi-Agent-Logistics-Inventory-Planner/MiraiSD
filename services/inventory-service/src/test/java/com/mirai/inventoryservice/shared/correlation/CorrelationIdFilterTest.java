package com.mirai.inventoryservice.shared.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdWhenNoneIsSupplied() throws Exception {
        when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq(CorrelationIdContext.HEADER_NAME), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void reusesTheIncomingIdWhenPresent() throws Exception {
        when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn("caller-supplied-id");

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(CorrelationIdContext.HEADER_NAME, "caller-supplied-id");
    }

    @Test
    void makesTheIdAvailableToDownstreamCodeViaMdcDuringTheRequest() throws Exception {
        when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn("mid-request-id");

        doAnswer(invocation -> {
            assertThat(CorrelationIdContext.current()).isEqualTo("mid-request-id");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void clearsMdcAfterTheRequestEvenIfTheChainThrows() throws Exception {
        when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn("id-to-clear");
        doThrow(new RuntimeException("boom")).when(filterChain).doFilter(request, response);

        try {
            filter.doFilter(request, response, filterChain);
        } catch (RuntimeException expected) {
            // expected: the filter must not swallow downstream exceptions
        }

        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }
}
