package com.mirai.inventoryservice.shared.correlation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies logging.pattern.level (application.properties) actually renders the correlation ID
 * MDC key into log output, not just that the filter sets it. A SpringBootTest is required here:
 * the console pattern is only applied when Spring Boot's real logging system initializes.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdLoggingTest.class);

    @Test
    void logLinesIncludeTheCorrelationIdWhenSetInMdc(CapturedOutput output) {
        MDC.put(CorrelationIdContext.MDC_KEY, "test-correlation-id-123");
        try {
            log.info("marker line for correlation id logging test");
        } finally {
            MDC.remove(CorrelationIdContext.MDC_KEY);
        }

        assertThat(output).contains("correlationId=test-correlation-id-123");
    }
}
