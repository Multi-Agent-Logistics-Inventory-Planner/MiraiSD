package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    // Find undelivered notifications
    List<Notification> findByDeliveredAtIsNullOrderByCreatedAtDesc();
    
    // Find notifications for a specific recipient
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    
    // Find undelivered notifications for a specific recipient
    List<Notification> findByRecipientIdAndDeliveredAtIsNullOrderByCreatedAtDesc(UUID recipientId);
    
    // Find by type
    List<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type);
    
    // Find by severity
    List<Notification> findBySeverityOrderByCreatedAtDesc(NotificationSeverity severity);
}

