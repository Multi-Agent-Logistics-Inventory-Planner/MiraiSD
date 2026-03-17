package com.mirai.inventoryservice.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.dtos.easypost.EasyPostWebhookPayload;
import com.mirai.inventoryservice.services.EasyPostWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for receiving EasyPost webhook events.
 *
 * Note: This endpoint is NOT secured by JWT authentication because EasyPost
 * sends webhooks without authentication headers. Security is provided by
 * HMAC signature validation in the service layer.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
public class EasyPostWebhookController {

    private final EasyPostWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public EasyPostWebhookController(
            EasyPostWebhookService webhookService,
            ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/easypost")
    public ResponseEntity<Void> handleEasyPostWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hmac-Signature", required = false) String signature) {

        log.info("Received EasyPost webhook");

        // Validate signature
        if (!webhookService.validateSignature(rawPayload, signature)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            EasyPostWebhookPayload payload = objectMapper.readValue(
                    rawPayload, EasyPostWebhookPayload.class);

            log.info("Processing webhook: id={}, type={}",
                    payload.getId(), payload.getDescription());

            webhookService.processWebhook(payload);

            return ResponseEntity.ok().build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            // Return 200 to prevent EasyPost retries for processing errors
            // The error is logged for investigation
            return ResponseEntity.ok().build();
        }
    }
}
