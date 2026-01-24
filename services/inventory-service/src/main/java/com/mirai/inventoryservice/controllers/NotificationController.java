package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.NotificationMapper;
import com.mirai.inventoryservice.dtos.responses.NotificationResponseDTO;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.services.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    public NotificationController(
            NotificationService notificationService,
            NotificationMapper notificationMapper) {
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getAllNotifications(
            @RequestParam(required = false) UUID recipientId) {
        List<Notification> notifications;
        if (recipientId != null) {
            notifications = notificationService.getNotificationsByRecipient(recipientId);
        } else {
            notifications = notificationService.getAllNotifications();
        }
        return ResponseEntity.ok(notificationMapper.toResponseDTOList(notifications));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDTO>> getUnreadNotifications(
            @RequestParam UUID recipientId) {
        List<Notification> notifications = notificationService.getUnreadNotifications(recipientId);
        return ResponseEntity.ok(notificationMapper.toResponseDTOList(notifications));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponseDTO> getNotificationById(@PathVariable UUID id) {
        Notification notification = notificationService.getNotificationById(id);
        return ResponseEntity.ok(notificationMapper.toResponseDTO(notification));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDTO> markAsRead(@PathVariable UUID id) {
        Notification notification = notificationService.markAsRead(id);
        return ResponseEntity.ok(notificationMapper.toResponseDTO(notification));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }
}

