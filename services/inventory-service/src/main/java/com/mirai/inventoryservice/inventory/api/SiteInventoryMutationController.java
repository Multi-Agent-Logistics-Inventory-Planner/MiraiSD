package com.mirai.inventoryservice.inventory.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.inventory.application.LocationInventoryService;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.shared.correlation.IdempotencyKeyContext;
import com.mirai.inventoryservice.shared.idempotency.CommandIdempotencyService;
import com.mirai.inventoryservice.shared.idempotency.CommandIdempotencyService.CommandResult;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * the same transaction as the underlying write. Actor identity is always
 * {@code context.backendUserId()} -- the authenticated principal, never a client-supplied
 * {@code actorId} field on the request body (docs/specs/authentication-and-authorization.md#4).
 * Distinct handler names from
 * {@link StockMovementController}'s so springdoc doesn't collide operation IDs, matching
 * {@link SiteInventoryController}.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class SiteInventoryMutationController {

    private final StockMovementService stockMovementService;
    private final LocationInventoryService locationInventoryService;
    private final CommandIdempotencyService commandIdempotencyService;
    private final ObjectMapper objectMapper;

    public SiteInventoryMutationController(
            StockMovementService stockMovementService,
            LocationInventoryService locationInventoryService,
            CommandIdempotencyService commandIdempotencyService,
            ObjectMapper objectMapper) {
        this.stockMovementService = stockMovementService;
        this.locationInventoryService = locationInventoryService;
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
                    stockMovementService.batchAdjustInventory(context.siteId(), context.backendUserId(), request);
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
                    stockMovementService.transferInventory(context.siteId(), context.backendUserId(), request);
                    return new CommandResult<>(HttpStatus.CREATED.value(), null);
                });
        return ResponseEntity.status(result.status()).build();
    }

    /**
     * v1 batch-transfer route (.specs/phase-6-inventory 6d, T-6d-be-5, user-confirmed): the
     * already-implemented {@link StockMovementService#batchTransferInventory(UUID, UUID,
     * BatchTransferInventoryRequestDTO)} exposed as a v1 route instead of remaining legacy-only or
     * being fanned into N single-transfer calls (which would lose atomicity and turn one audit
     * entry into N). Requires {@link #fingerprint}'s recursive {@code actorId} stripping
     * (T-6d-be-4), since each {@code transfers[]} element carries its own {@code actorId}.
     */
    @PostMapping("/transfers/batch")
    public ResponseEntity<Void> batchTransferSiteInventory(
            @PathVariable UUID siteId,
            @RequestHeader(IdempotencyKeyContext.HEADER_NAME) String idempotencyKey,
            @Valid @RequestBody BatchTransferInventoryRequestDTO request) {
        AuthorizedSiteContext context = AuthorizedSiteContextHolder.require();
        CommandResult<Void> result = commandIdempotencyService.executeIdempotent(
                context.siteId(),
                context.backendUserId(),
                idempotencyKey,
                "inventory.transfer.batch",
                fingerprint(request),
                Void.class,
                () -> {
                    stockMovementService.batchTransferInventory(context.siteId(), context.backendUserId(), request);
                    return new CommandResult<>(HttpStatus.CREATED.value(), null);
                });
        return ResponseEntity.status(result.status()).build();
    }

    /**
     * v1 create route for R-9's resolution (.specs/phase-6-inventory 6d, T-6d-be-2): mirrors
     * legacy {@code LocationInventoryController.addInventory} but site-scoped and returning the
     * existing slim {@link SiteLocationInventoryEntryDTO} (no catalog metadata -- R-9, same
     * discipline as {@link SiteInventoryController#getSiteInventoryByLocation}).
     */
    @PostMapping("/locations/{locationId}/items")
    public ResponseEntity<SiteLocationInventoryEntryDTO> createSiteLocationInventoryItem(
            @PathVariable UUID siteId,
            @PathVariable UUID locationId,
            @RequestHeader(IdempotencyKeyContext.HEADER_NAME) String idempotencyKey,
            @Valid @RequestBody CreateLocationInventoryRequestDTO request) {
        AuthorizedSiteContext context = AuthorizedSiteContextHolder.require();
        CommandResult<SiteLocationInventoryEntryDTO> result = commandIdempotencyService.executeIdempotent(
                context.siteId(),
                context.backendUserId(),
                idempotencyKey,
                "inventory.location.create",
                fingerprint(new CreateInventoryFingerprintKey(locationId, request)),
                SiteLocationInventoryEntryDTO.class,
                () -> {
                    LocationInventory created = locationInventoryService.addInventory(
                            context.siteId(), context.backendUserId(), locationId,
                            request.getProductId(), request.getQuantity(), request.getReason(),
                            request.getIntakeUnit(), request.getIntakeQty());
                    return new CommandResult<>(HttpStatus.CREATED.value(), toEntryDTO(created));
                });
        return ResponseEntity.status(result.status()).body(result.body());
    }

    /**
     * v1 delete route for R-9's resolution (.specs/phase-6-inventory 6d, T-6d-be-3): restricted to
     * ADMIN/ASSISTANT_MANAGER, overriding the class-level default that also allows EMPLOYEE --
     * matches the legacy {@code LocationInventoryController.deleteInventory} role restriction.
     */
    @DeleteMapping("/locations/{locationId}/items/{inventoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteSiteLocationInventoryItem(
            @PathVariable UUID siteId,
            @PathVariable UUID locationId,
            @PathVariable UUID inventoryId,
            @RequestHeader(IdempotencyKeyContext.HEADER_NAME) String idempotencyKey) {
        AuthorizedSiteContext context = AuthorizedSiteContextHolder.require();
        CommandResult<Void> result = commandIdempotencyService.executeIdempotent(
                context.siteId(),
                context.backendUserId(),
                idempotencyKey,
                "inventory.location.delete",
                fingerprint(new DeleteInventoryFingerprintKey(locationId, inventoryId)),
                Void.class,
                () -> {
                    locationInventoryService.deleteInventory(
                            context.siteId(), context.backendUserId(), locationId, inventoryId, null);
                    return new CommandResult<>(HttpStatus.NO_CONTENT.value(), null);
                });
        return ResponseEntity.status(result.status()).build();
    }

    /**
     * Deterministic fingerprint carrier for the create route (.specs/phase-6-inventory 6d,
     * review-driven fix, finding 2): {@code locationId} is a path variable, not part of
     * {@code request}'s body, so fingerprinting {@code request} alone let the same
     * {@code Idempotency-Key} + identical body match across two different {@code locationId}
     * values and silently reuse the first location's stored response instead of creating
     * anything at the second. A Java record's component order is fixed by its declaration (unlike
     * {@link java.util.Map#of}'s randomized-per-JVM iteration order), so Jackson serializes it the
     * same way on every run.
     */
    // Package-private (not private) so SiteInventoryMutationControllerFingerprintTest can pin its
    // hash directly, matching fingerprint(Object)'s own visibility.
    record CreateInventoryFingerprintKey(UUID locationId, CreateLocationInventoryRequestDTO request) {
    }

    /**
     * Deterministic fingerprint carrier for the delete route (.specs/phase-6-inventory 6d,
     * review-driven fix, finding 1): the prior {@code java.util.Map.of("locationId", locationId,
     * "inventoryId", inventoryId)} carrier has a JVM-randomized iteration order (seeded from
     * {@code System.nanoTime()}), so the same logical delete command fingerprinted differently
     * across JVM restarts/redeploys -- a legitimate retry inside the idempotency table's 7-day
     * retention window could get a spurious 409 instead of an idempotent 204. A record's
     * component order is fixed by its declaration, so this hashes identically every time.
     */
    // Package-private (not private) so SiteInventoryMutationControllerFingerprintTest can pin its
    // hash directly, matching fingerprint(Object)'s own visibility.
    record DeleteInventoryFingerprintKey(UUID locationId, UUID inventoryId) {
    }

    private static SiteLocationInventoryEntryDTO toEntryDTO(LocationInventory inventory) {
        return SiteLocationInventoryEntryDTO.builder()
                .inventoryId(inventory.getId())
                .productId(inventory.getProduct().getId())
                .quantity(inventory.getQuantity())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    /**
     * A stable, size-bounded fingerprint of the request body: the canonical JSON serialization
     * with {@code actorId} excluded, SHA-256 hashed to a fixed-length hex string (the entity
     * column carries no explicit length override, so hashing keeps this well under any default
     * column-size limit regardless of how large a batch request gets).
     * <p>
     * {@code actorId} is excluded deliberately, not incidentally: the service methods always
     * overwrite it with the authenticated principal (see the class javadoc), so it carries no
     * request identity of its own -- including it would make the fingerprint depend on a field
     * the caller cannot actually influence the persisted effect of, and would make a fingerprint
     * computed before that overwrite mismatch one computed after it if the same request object
     * were ever fingerprinted twice.
     * <p>
     * Stripping happens recursively at every JSON depth (.specs/phase-6-inventory 6d, T-6d-be-4),
     * not just the top level: {@link BatchTransferInventoryRequestDTO#getTransfers()} nests each
     * {@code actorId} inside a {@code transfers[]} element, so a top-level-only strip would let an
     * ignored-but-present client {@code actorId} leak into the fingerprint and make a legitimate
     * retry collide with a spurious 409.
     */
    // Package-private (not private) so SiteInventoryMutationControllerFingerprintTest
    // (.specs/phase-6-inventory 6d, T-6d-be-4) can exercise it directly.
    String fingerprint(Object request) {
        try {
            com.fasterxml.jackson.databind.JsonNode tree = objectMapper.valueToTree(request);
            stripActorIdRecursively(tree);
            byte[] json = objectMapper.writeValueAsBytes(tree);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize request for idempotency fingerprint", e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every supported JDK", e);
        }
    }

    /**
     * Recursively removes every {@code actorId} field from {@code node}, at any depth -- walking
     * into object fields and array elements alike. Mutates {@code node} in place (Jackson's
     * {@link com.fasterxml.jackson.databind.node.ObjectNode}/{@link com.fasterxml.jackson.databind.node.ArrayNode}
     * are mutable containers), matching {@link #fingerprint(Object)}'s prior top-level-only
     * {@code Map.remove("actorId")} behavior but extended to every nesting level.
     */
    private void stripActorIdRecursively(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode objectNode =
                    (com.fasterxml.jackson.databind.node.ObjectNode) node;
            objectNode.remove("actorId");
            objectNode.elements().forEachRemaining(this::stripActorIdRecursively);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::stripActorIdRecursively);
        }
    }
}
