package com.mirai.inventoryservice.kuji.api;

import com.mirai.inventoryservice.kuji.application.KujiBoxService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the .specs/phase-7-kuji-lootbox review finding P1: every v1 mutation
 * except reopen/undo passed its request DTO straight through to {@link KujiBoxService}, and each
 * of those DTOs carries a legacy {@code actorId} field the service writes into audit logs and
 * stock movements. A caller could attribute an action to an arbitrary user id. The controller now
 * overwrites {@code actorId} with the authenticated principal from the resolved site context
 * before calling the service, for every mutation.
 */
@ExtendWith(MockitoExtension.class)
class SiteKujiBoxControllerActorSpoofingTest {

    private static final UUID REAL_ACTOR_ID = UUID.randomUUID();
    private static final UUID SPOOFED_ACTOR_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();

    @Mock
    private KujiBoxService kujiBoxService;

    private SiteKujiBoxController controller;

    @BeforeEach
    void setUp() {
        controller = new SiteKujiBoxController(kujiBoxService);
        AuthorizedSiteContextHolder.set(new AuthorizedSiteContext(
                REAL_ACTOR_ID, SITE_ID, "ADMIN", Set.of(), false, "test-correlation"));
    }

    @AfterEach
    void tearDown() {
        AuthorizedSiteContextHolder.clear();
    }

    @Test
    void openSiteBox_overwritesSpoofedActorId() {
        OpenKujiBoxRequestDTO request = OpenKujiBoxRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.openBox(eq(SITE_ID), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.openSiteBox(SITE_ID, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void closeSiteBox_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        CloseKujiBoxRequestDTO request = CloseKujiBoxRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.closeBox(eq(SITE_ID), eq(boxId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.closeSiteBox(SITE_ID, boxId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void addSiteTier_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        AddKujiTierRequestDTO request = AddKujiTierRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.addTier(eq(SITE_ID), eq(boxId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.addSiteTier(SITE_ID, boxId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void patchSiteTier_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        PatchKujiTierRequestDTO request = PatchKujiTierRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.patchTier(eq(SITE_ID), eq(boxId), eq(tierId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.patchSiteTier(SITE_ID, boxId, tierId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void transferInMoreToSiteTier_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        TransferInMoreRequestDTO request = TransferInMoreRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.transferInMore(eq(SITE_ID), eq(boxId), eq(tierId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.transferInMoreToSiteTier(SITE_ID, boxId, tierId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void transferInInventoryOnlyToSiteTier_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        TransferInMoreRequestDTO request = TransferInMoreRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.transferInInventoryOnly(eq(SITE_ID), eq(boxId), eq(tierId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.transferInInventoryOnlyToSiteTier(SITE_ID, boxId, tierId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void addSlipToSiteTier_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        AddSlipRequestDTO request = AddSlipRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.addSlip(eq(SITE_ID), eq(boxId), eq(tierId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.addSlipToSiteTier(SITE_ID, boxId, tierId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void moveSlipsInSiteTier_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        MoveSlipsRequestDTO request = MoveSlipsRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.moveSlips(eq(SITE_ID), eq(boxId), eq(tierId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.moveSlipsInSiteTier(SITE_ID, boxId, tierId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void deleteSitePrize_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        DeletePrizeRequestDTO request = DeletePrizeRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.deletePrize(eq(SITE_ID), eq(boxId), eq(tierId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.deleteSitePrize(SITE_ID, boxId, tierId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void recordSiteDraw_overwritesSpoofedActorId() {
        UUID boxId = UUID.randomUUID();
        RecordDrawRequestDTO request = RecordDrawRequestDTO.builder().actorId(SPOOFED_ACTOR_ID).build();
        when(kujiBoxService.recordDraw(eq(SITE_ID), eq(boxId), any())).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.recordSiteDraw(SITE_ID, boxId, request, null);

        assertThat(request.getActorId()).isEqualTo(REAL_ACTOR_ID);
    }

    @Test
    void reopenSiteBox_neverAcceptsAClientActorIdAtAll_usesContextDirectly() {
        UUID boxId = UUID.randomUUID();
        when(kujiBoxService.reopenBox(SITE_ID, boxId, REAL_ACTOR_ID)).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.reopenSiteBox(SITE_ID, boxId, null);

        // No client body at all for this route - verifying the service was called with the
        // context actor is enough; a spoofed value has nowhere to enter in the first place.
        org.mockito.Mockito.verify(kujiBoxService).reopenBox(SITE_ID, boxId, REAL_ACTOR_ID);
    }

    @Test
    void undoSiteDraw_neverAcceptsAClientActorIdAtAll_usesContextDirectly() {
        UUID boxId = UUID.randomUUID();
        UUID auditLogId = UUID.randomUUID();
        when(kujiBoxService.undoDraw(SITE_ID, boxId, auditLogId, REAL_ACTOR_ID)).thenReturn(mock(KujiBoxResponseDTO.class));

        controller.undoSiteDraw(SITE_ID, boxId, auditLogId, null);

        org.mockito.Mockito.verify(kujiBoxService).undoDraw(SITE_ID, boxId, auditLogId, REAL_ACTOR_ID);
    }
}
