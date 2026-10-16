package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.audit.application.AuditLogService;
import com.mirai.inventoryservice.repositories.NotificationRepository;
import com.mirai.inventoryservice.shipments.infrastructure.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Review finding (.specs/phase-7-shipments T-4 module-move review): the activity feed's shipment
 * read used the unscoped {@code ShipmentRepository.findAllWithAssociations(Pageable)}, and
 * {@code ActivityFeedController} had no site resolution at all -- any authenticated user, with
 * zero site memberships, could see every site's shipment activity. Proves the fix: the shipment
 * portion now goes through the site-scoped {@code findByStatusPaged} with the caller's site.
 * Audit-log and notification reads are site-scoped too (notifications-reviews-audit slice).
 */
@ExtendWith(MockitoExtension.class)
class ActivityFeedServiceTest {

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private ActivityFeedService activityFeedService;

    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(shipmentRepository.findByStatusPaged(isNull(), eq(siteId), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    @Test
    void shipmentReadIsScopedToTheCallersSite() {
        activityFeedService.getActivityFeed(siteId, 50, List.of("shipment"), false);

        ArgumentCaptor<UUID> siteCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shipmentRepository).findByStatusPaged(isNull(), siteCaptor.capture(), any(Pageable.class));
        org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue()).isEqualTo(siteId);
    }

    @Test
    void auditLogReadIsScopedToTheCallersSite() {
        when(auditLogService.findRecent(
                eq(siteId), any(OffsetDateTime.class), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        activityFeedService.getActivityFeed(siteId, 50, List.of("restock"), false);

        verify(auditLogService).findRecent(
                eq(siteId), any(OffsetDateTime.class), any(OffsetDateTime.class), any(Pageable.class));
    }

    @Test
    void unresolvedNotificationReadIsScopedToTheCallersSite() {
        when(notificationRepository.findUnresolvedBySiteIdOrderByCreatedAtDesc(eq(siteId), any(Pageable.class)))
                .thenReturn(Page.empty());

        activityFeedService.getActivityFeed(siteId, 50, List.of("alert"), false);

        verify(notificationRepository).findUnresolvedBySiteIdOrderByCreatedAtDesc(eq(siteId), any(Pageable.class));
    }

    @Test
    void allNotificationReadIsScopedToTheCallersSite() {
        when(notificationRepository.findBySiteIdOrderByCreatedAtDesc(eq(siteId), any(Pageable.class)))
                .thenReturn(Page.empty());

        activityFeedService.getActivityFeed(siteId, 50, List.of("alert"), true);

        verify(notificationRepository).findBySiteIdOrderByCreatedAtDesc(eq(siteId), any(Pageable.class));
    }

    @Test
    void movementsFromAnotherSiteAreDroppedFromAMixedAuditLog() {
        var product = com.mirai.inventoryservice.catalog.domain.Product.builder()
                .id(UUID.randomUUID()).name("Plush").build();
        var own = com.mirai.inventoryservice.inventory.domain.StockMovement.builder()
                .id(1L).reason(com.mirai.inventoryservice.models.enums.StockMovementReason.SALE)
                .quantityChange(-1).item(product)
                .site(com.mirai.inventoryservice.sites.domain.Site.builder().id(siteId).build()).build();
        var foreign = com.mirai.inventoryservice.inventory.domain.StockMovement.builder()
                .id(2L).reason(com.mirai.inventoryservice.models.enums.StockMovementReason.SALE)
                .quantityChange(-1).item(product)
                .site(com.mirai.inventoryservice.sites.domain.Site.builder().id(UUID.randomUUID()).build()).build();
        var log = com.mirai.inventoryservice.audit.domain.AuditLog.builder()
                .id(UUID.randomUUID()).siteId(siteId).createdAt(OffsetDateTime.now())
                .movements(new java.util.ArrayList<>(List.of(own, foreign))).build();
        when(auditLogService.findRecent(
                eq(siteId), any(OffsetDateTime.class), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(log)));

        var events = activityFeedService.getActivityFeed(siteId, 50, List.of("sale"), false);

        org.assertj.core.api.Assertions.assertThat(events).extracting("id").containsExactly("audit-1");
    }
}
