package com.mirai.inventoryservice.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Generic exception should NOT expose internal error details")
    void handleGenericException_shouldNotExposeInternalDetails() {
        // Arrange - exception with sensitive info
        Exception sensitiveException = new RuntimeException(
            "JDBC Connection failed: host=db.internal.com, password=secret123"
        );

        // Act
        var response = handler.handleGenericException(sensitiveException);
        var body = response.getBody();

        // Assert - message must be generic
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(body.getMessage()).doesNotContain("JDBC");
        assertThat(body.getMessage()).doesNotContain("password");
        assertThat(body.getMessage()).doesNotContain("host");
    }

    @Test
    @DisplayName("Generic exception should return HTTP 500")
    void handleGenericException_shouldReturn500() {
        var response = handler.handleGenericException(new RuntimeException("any"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    @DisplayName("Generic exception response should have correct error type")
    void handleGenericException_shouldHaveCorrectErrorType() {
        var response = handler.handleGenericException(new RuntimeException("test"));
        var body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.getError()).isEqualTo("Internal Server Error");
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getTimestamp()).isNotNull();
    }
}
