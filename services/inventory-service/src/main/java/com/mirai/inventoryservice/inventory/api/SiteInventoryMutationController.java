package com.mirai.inventoryservice.inventory.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.shared.correlation.IdempotencyKeyContext;
import com.mirai.inventoryservice.shared.idempotency.CommandIdempotencyService;
import com.mirai.inventoryservice.shared.idempotency.CommandIdempotencyService.CommandResult;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Site-scoped inventory mutation routes (.specs/phase-6-inventory 6c, T-6c-12, AC-3/AC-5).
 * Same-site-only per T-6c-4 (enforced inside {@link StockMovementService}'s site-scoped
 * overloads, which 404 before any write if the named inventory isn't at this site). Every
 * mutation requires the {@code Idempotency-Key} header (Q-6c-3) and is executed through
 * {@link CommandIdempotencyService#executeIdempotent}, which commits the idempotency record in
 * the same transaction as the underlying write. Distinct handler names from
 * {@link StockMovementController}'s so springdoc doesn't collide operation IDs, matching
 * {@link SiteInventoryController}.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class SiteInventoryMutationController {

    private final StockMovementService stockMovementService;
    private final CommandIdempotencyService commandIdempotencyService;
    private final ObjectMapper objectMapper;

    public SiteInventoryMutationController(
            StockMovementService stockMovementService,
            CommandIdempotencyService commandIdempotencyService,
            ObjectMapper objectMapper) {
        this.stockMovementService = stockMovementService;
        this.commandIdempotencyService = commandIdempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/adjustments")
    public ResponseEntity<Void> adjustSiteInventory(
            @PathVariable UUID siteId,
            @RequestHeader(IdempotencyKeyContext.HEADER_NAME) String idempotencyKey,
            @Valid @RequestBody BatchAdjustStockRequestDTO request) {
        AuthorizedSiteContext context = AuthorizedSiteContextHolder.require();
        CommandResult<Void> result = commandIdempotencyService.executeIdempotent(
                context.siteId(),
                context.backendUserId(),
                idempotencyKey,
                "inventory.adjust",
                fingerprint(request),
                Void.class,
                () -> {
                    stockMovementService.batchAdjustInventory(context.siteId(), request);
                    return new CommandResult<>(HttpStatus.CREATED.value(), null);
                });
        return ResponseEntity.status(result.status()).build();
    }

    @PostMapping("/transfers")
    public ResponseEntity<Void> transferSiteInventory(
            @PathVariable UUID siteId,
            @RequestHeader(IdempotencyKeyContext.HEADER_NAME) String idempotencyKey,
            @Valid @RequestBody TransferInventoryRequestDTO request) {
        AuthorizedSiteContext context = AuthorizedSiteContextHolder.require();
        CommandResult<Void> result = commandIdempotencyService.executeIdempotent(
                context.siteId(),
                context.backendUserId(),
                idempotencyKey,
                "inventory.transfer",
                fingerprint(request),
                Void.class,
                () -> {
                    stockMovementService.transferInventory(context.siteId(), request);
                    return new CommandResult<>(HttpStatus.CREATED.value(), null);
                });
        return ResponseEntity.status(result.status()).build();
    }

    /**
     * A stable, size-bounded fingerprint of the request body: the canonical JSON serialization,
     * SHA-256 hashed to a fixed-length hex string (the entity column carries no explicit length
     * override, so hashing keeps this well under any default column-size limit regardless of how
     * large a batch request gets).
     */
    private String fingerprint(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize request for idempotency fingerprint", e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every supported JDK", e);
        }
    }
}
