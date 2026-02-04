package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.NotificationMapper;
import com.mirai.inventoryservice.dtos.requests.NotificationFilterDTO;
import com.mirai.inventoryservice.dtos.responses.NotificationResponseDTO;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.services.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/search")
    public ResponseEntity<Page<NotificationResponseDTO>> searchNotifications(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        NotificationFilterDTO filters = NotificationFilterDTO.builder()
                .search(search)
                .type(type)
                .resolved(resolved)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
        Page<Notification> notifications = notificationService.getNotifications(filters, pageable);
        return ResponseEntity.ok(notifications.map(notificationMapper::toResponseDTO));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<NotificationResponseDTO> resolveNotification(@PathVariable UUID id) {
        Notification notification = notificationService.resolveNotification(id);
        return ResponseEntity.ok(notificationMapper.toResponseDTO(notification));
    }

    @PutMapping("/{id}/unresolve")
    public ResponseEntity<NotificationResponseDTO> unresolveNotification(@PathVariable UUID id) {
        Notification notification = notificationService.unresolveNotification(id);
        return ResponseEntity.ok(notificationMapper.toResponseDTO(notification));
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> getNotificationCounts() {
        Map<String, Long> counts = Map.of(
                "active", notificationService.countActive(),
                "resolved", notificationService.countResolved()
        );
        return ResponseEntity.ok(counts);
    }
}

