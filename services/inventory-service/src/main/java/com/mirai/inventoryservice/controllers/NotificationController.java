package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.NotificationMapper;
import com.mirai.inventoryservice.dtos.requests.NotificationFilterDTO;
import com.mirai.inventoryservice.dtos.responses.NotificationResponseDTO;
import com.mirai.inventoryservice.models.audit.Notification;
import com.mirai.inventoryservice.models.enums.NotificationType;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.services.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final ProductRepository productRepository;

    public NotificationController(
            NotificationService notificationService,
            NotificationMapper notificationMapper,
            ProductRepository productRepository) {
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
        this.productRepository = productRepository;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponseDTO>> getAllNotifications(
            @RequestParam(required = false) UUID recipientId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Notification> notifications;
        if (recipientId != null) {
            notifications = notificationService.getNotificationsByRecipient(recipientId, pageable);
        } else {
            notifications = notificationService.getAllNotifications(pageable);
        }
        Page<NotificationResponseDTO> response = notifications.map(notificationMapper::toResponseDTO);
        populateItemNames(response.getContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDTO>> getUnreadNotifications(
            @RequestParam UUID recipientId) {
        List<Notification> notifications = notificationService.getUnreadNotifications(recipientId);
        List<NotificationResponseDTO> response = notificationMapper.toResponseDTOList(notifications);
        populateItemNames(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponseDTO> getNotificationById(@PathVariable UUID id) {
        Notification notification = notificationService.getNotificationById(id);
        NotificationResponseDTO response = notificationMapper.toResponseDTO(notification);
        populateItemName(response);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDTO> markAsRead(@PathVariable UUID id) {
        Notification notification = notificationService.markAsRead(id);
        NotificationResponseDTO response = notificationMapper.toResponseDTO(notification);
        populateItemName(response);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/mark-read")
    public ResponseEntity<NotificationResponseDTO> markAsUserRead(@PathVariable UUID id) {
        Notification notification = notificationService.markAsUserRead(id);
        NotificationResponseDTO response = notificationMapper.toResponseDTO(notification);
        populateItemName(response);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
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
        Page<NotificationResponseDTO> response = notifications.map(notificationMapper::toResponseDTO);
        populateItemNames(response.getContent());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<NotificationResponseDTO> resolveNotification(@PathVariable UUID id) {
        Notification notification = notificationService.resolveNotification(id);
        NotificationResponseDTO response = notificationMapper.toResponseDTO(notification);
        populateItemName(response);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/unresolve")
    public ResponseEntity<NotificationResponseDTO> unresolveNotification(@PathVariable UUID id) {
        Notification notification = notificationService.unresolveNotification(id);
        NotificationResponseDTO response = notificationMapper.toResponseDTO(notification);
        populateItemName(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> getNotificationCounts() {
        Map<String, Long> counts = Map.of(
                "active", notificationService.countActive(),
                "resolved", notificationService.countResolved(),
                "unread", notificationService.countUnread()
        );
        return ResponseEntity.ok(counts);
    }

    private void populateItemName(NotificationResponseDTO dto) {
        if (dto == null || dto.getItemId() == null) return;
        productRepository.findById(dto.getItemId())
                .ifPresent(product -> dto.setItemName(product.getName()));
    }

    private void populateItemNames(List<NotificationResponseDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        Set<UUID> itemIds = dtos.stream()
                .map(NotificationResponseDTO::getItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (itemIds.isEmpty()) return;

        Map<UUID, String> itemNameById = productRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));

        for (NotificationResponseDTO dto : dtos) {
            UUID itemId = dto.getItemId();
            if (itemId != null) {
                dto.setItemName(itemNameById.get(itemId));
            }
        }
    }
}

