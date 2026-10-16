package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceSiteTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private SupabaseBroadcastService broadcastService;

    @Test
    void createNotificationBroadcastsWithTheNotificationsSite() {
        UUID siteId = UUID.randomUUID();
        Notification notification = Notification.builder().siteId(siteId).build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        new NotificationService(notificationRepository, broadcastService).createNotification(notification);

        verify(broadcastService).broadcastNotificationCreated(siteId);
    }
}
