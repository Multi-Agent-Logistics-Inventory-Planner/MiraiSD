package com.mirai.inventoryservice.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.inventory.application.LocationInventoryService;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.shared.idempotency.CommandIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link SiteInventoryMutationController#fingerprint(Object)}
 * (.specs/phase-6-inventory 6d, T-6d-be-4): recursive {@code actorId} stripping must hold at any
 * JSON depth, not just the top level -- required before T-6d-be-5's batch-transfer route ships,
 * since {@link BatchTransferInventoryRequestDTO#getTransfers()} nests each {@code actorId} inside
 * a {@code transfers[]} element.
 */
class SiteInventoryMutationControllerFingerprintTest {

    private SiteInventoryMutationController controller;

    @BeforeEach
    void setUp() {
        controller = new SiteInventoryMutationController(
                mock(StockMovementService.class),
                mock(LocationInventoryService.class),
                mock(CommandIdempotencyService.class),
                new ObjectMapper());
    }

    private TransferInventoryRequestDTO transfer(UUID sourceId, UUID destinationId, UUID actorId, int quantity) {
        return TransferInventoryRequestDTO.builder()
                .sourceLocationType(LocationType.BOX_BIN)
                .sourceInventoryId(sourceId)
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationLocationId(destinationId)
                .quantity(quantity)
                .actorId(actorId)
                .build();
    }

    @Test
    void nestedActorIdOnlyDifference_hashesIdentically() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        BatchTransferInventoryRequestDTO first = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(transfer(sourceId, destinationId, UUID.randomUUID(), 3)))
                .build();
        BatchTransferInventoryRequestDTO second = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(transfer(sourceId, destinationId, UUID.randomUUID(), 3)))
                .build();

        assertThat(controller.fingerprint(first)).isEqualTo(controller.fingerprint(second));
    }

    @Test
    void nestedRealFieldDifference_stillHashesDifferently() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UUID sharedActorId = UUID.randomUUID();
        BatchTransferInventoryRequestDTO first = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(transfer(sourceId, destinationId, sharedActorId, 3)))
                .build();
        BatchTransferInventoryRequestDTO second = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(transfer(sourceId, destinationId, sharedActorId, 4)))
                .build();

        assertThat(controller.fingerprint(first)).isNotEqualTo(controller.fingerprint(second));
    }

    @Test
    void topLevelActorIdOnlyDifference_stillHashesIdentically() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        TransferInventoryRequestDTO first = transfer(sourceId, destinationId, UUID.randomUUID(), 3);
        TransferInventoryRequestDTO second = transfer(sourceId, destinationId, UUID.randomUUID(), 3);

        assertThat(controller.fingerprint(first)).isEqualTo(controller.fingerprint(second));
    }

    @Test
    void topLevelRealFieldDifference_stillHashesDifferently() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UUID sharedActorId = UUID.randomUUID();
        TransferInventoryRequestDTO first = transfer(sourceId, destinationId, sharedActorId, 3);
        TransferInventoryRequestDTO second = transfer(sourceId, destinationId, sharedActorId, 4);

        assertThat(controller.fingerprint(first)).isNotEqualTo(controller.fingerprint(second));
    }

    // ========= review-driven fix: deterministic path-variable fingerprint carriers =========
    // (.specs/phase-6-inventory 6d, review-driven fix, findings 1/2). These pin the fingerprint of
    // a fixed logical command to a hard-coded SHA-256 literal, computed once from the exact
    // production serialization path (SiteInventoryMutationController.fingerprint's own
    // ObjectMapper.valueToTree + recursive actorId-strip + SHA-256), so an ordering regression --
    // e.g. reverting to java.util.Map.of, whose iteration order is randomized per JVM from
    // System.nanoTime() -- is structurally impossible to reintroduce undetected: a test that just
    // re-derived the hash the same way the code does would not catch that class of bug, since it
    // would drift together with the code on every JVM run.

    private static final UUID FIXED_LOCATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIXED_INVENTORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FIXED_PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void deleteFingerprint_forFixedLocationAndInventoryId_matchesPinnedHash() {
        String fingerprint = controller.fingerprint(
                new SiteInventoryMutationController.DeleteInventoryFingerprintKey(
                        FIXED_LOCATION_ID, FIXED_INVENTORY_ID));

        assertThat(fingerprint)
                .isEqualTo("8210e6eb400c43f3226db439f1916da14bf6279834a94459f8793822d413cd59");
    }

    @Test
    void deleteFingerprint_isStableAcrossRepeatedCalls_forTheSameLogicalCommand() {
        String first = controller.fingerprint(
                new SiteInventoryMutationController.DeleteInventoryFingerprintKey(
                        FIXED_LOCATION_ID, FIXED_INVENTORY_ID));
        String second = controller.fingerprint(
                new SiteInventoryMutationController.DeleteInventoryFingerprintKey(
                        FIXED_LOCATION_ID, FIXED_INVENTORY_ID));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void createFingerprint_includesLocationId_soSameBodyDifferentLocationHashesDifferently() {
        CreateLocationInventoryRequestDTO body = CreateLocationInventoryRequestDTO.builder()
                .productId(FIXED_PRODUCT_ID)
                .quantity(5)
                .build();
        UUID otherLocationId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        String first = controller.fingerprint(
                new SiteInventoryMutationController.CreateInventoryFingerprintKey(FIXED_LOCATION_ID, body));
        String second = controller.fingerprint(
                new SiteInventoryMutationController.CreateInventoryFingerprintKey(otherLocationId, body));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createFingerprint_forFixedLocationAndBody_matchesPinnedHash() {
        CreateLocationInventoryRequestDTO body = CreateLocationInventoryRequestDTO.builder()
                .productId(FIXED_PRODUCT_ID)
                .quantity(5)
                .build();

        String fingerprint = controller.fingerprint(
                new SiteInventoryMutationController.CreateInventoryFingerprintKey(FIXED_LOCATION_ID, body));

        assertThat(fingerprint)
                .isEqualTo("2ab37ee3a25a55d3943fe2421108d666ae1d05338f2375d27acb85ab856540ff");
    }
}
