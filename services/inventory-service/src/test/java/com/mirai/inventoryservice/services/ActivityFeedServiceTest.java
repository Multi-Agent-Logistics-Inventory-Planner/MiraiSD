package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.repositories.AuditLogRepository;
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
 * Audit-log and notification reads stay unscoped here (those entities carry no {@code site_id}
 * yet -- notifications-reviews-audit slice's responsibility, not this one's).
 */
@ExtendWith(MockitoExtension.class)
class ActivityFeedServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private ActivityFeedService activityFeedService;

    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(shipmentRepository.findByStatusPaged(isNull(), eq(siteId), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    @Test
    void shipmentReadIsScopedToTheCallersSite() {
        activityFeedService.getActivityFeed(siteId, 50, List.of("shipment"), false);

        ArgumentCaptor<UUID> siteCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(shipmentRepository).findByStatusPaged(isNull(), siteCaptor.capture(), any(Pageable.class));
        org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue()).isEqualTo(siteId);
    }
}
