package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.NotificationFilterDTO;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.repositories.NotificationRepository;
import com.mirai.inventoryservice.repositories.NotificationSpecifications;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public List<Notification> getNotificationsByRecipient(UUID recipientId) {
        // Include broadcast notifications (recipient_id IS NULL) along with user-specific ones
        return notificationRepository.findByRecipientIdOrBroadcastOrderByCreatedAtDesc(recipientId);
    }

    public List<Notification> getUnreadNotifications(UUID recipientId) {
        // Include broadcast notifications (recipient_id IS NULL) along with user-specific ones
        return notificationRepository.findUnreadByRecipientIdOrBroadcastOrderByCreatedAtDesc(recipientId);
    }

    public Notification getNotificationById(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
    }

    public Notification markAsRead(UUID id) {
        Notification notification = getNotificationById(id);
        notification.setDeliveredAt(OffsetDateTime.now());
        return notificationRepository.save(notification);
    }

    public void deleteNotification(UUID id) {
        Notification notification = getNotificationById(id);
        notificationRepository.delete(notification);
    }

    public void markAllAsRead() {
        List<Notification> unreadNotifications = notificationRepository.findByDeliveredAtIsNullOrderByCreatedAtDesc();
        OffsetDateTime now = OffsetDateTime.now();
        for (Notification notification : unreadNotifications) {
            notification.setDeliveredAt(now);
        }
        notificationRepository.saveAll(unreadNotifications);
    }

    public Page<Notification> getNotifications(NotificationFilterDTO filters, Pageable pageable) {
        return notificationRepository.findAll(NotificationSpecifications.withFilters(filters), pageable);
    }

    public Notification resolveNotification(UUID id) {
        Notification notification = getNotificationById(id);
        notification.setResolvedAt(OffsetDateTime.now());
        return notificationRepository.save(notification);
    }

    public Notification unresolveNotification(UUID id) {
        Notification notification = getNotificationById(id);
        notification.setResolvedAt(null);
        return notificationRepository.save(notification);
    }

    public long countActive() {
        return notificationRepository.countByResolvedAtIsNull();
    }

    public long countResolved() {
        return notificationRepository.countByResolvedAtIsNotNull();
    }

    public Notification createNotification(Notification notification) {
        return notificationRepository.save(notification);
    }
}

