package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    // Find undelivered notifications
    List<Notification> findByDeliveredAtIsNullOrderByCreatedAtDesc();

    // Find notifications for a specific recipient
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    // Find notifications for a specific recipient OR broadcast notifications (recipient_id IS NULL)
    @Query("SELECT n FROM Notification n WHERE n.recipientId = :recipientId OR n.recipientId IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findByRecipientIdOrBroadcastOrderByCreatedAtDesc(@Param("recipientId") UUID recipientId);

    // Find undelivered notifications for a specific recipient
    List<Notification> findByRecipientIdAndDeliveredAtIsNullOrderByCreatedAtDesc(UUID recipientId);

    // Find undelivered notifications for a specific recipient OR broadcast notifications
    @Query("SELECT n FROM Notification n WHERE (n.recipientId = :recipientId OR n.recipientId IS NULL) AND n.deliveredAt IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByRecipientIdOrBroadcastOrderByCreatedAtDesc(@Param("recipientId") UUID recipientId);

    // Find by type
    List<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type);

    // Find by severity
    List<Notification> findBySeverityOrderByCreatedAtDesc(NotificationSeverity severity);
}

