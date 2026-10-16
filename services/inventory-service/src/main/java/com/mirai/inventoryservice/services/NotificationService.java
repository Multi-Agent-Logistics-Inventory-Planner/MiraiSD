package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.repositories.NotificationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SupabaseBroadcastService broadcastService;

    public NotificationService(NotificationRepository notificationRepository, SupabaseBroadcastService broadcastService) {
        this.notificationRepository = notificationRepository;
        this.broadcastService = broadcastService;
    }

    public Notification createNotification(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        broadcastService.broadcastNotificationCreated(saved.getSiteId());
        return saved;
    }
}

