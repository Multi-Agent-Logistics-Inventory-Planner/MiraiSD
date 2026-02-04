package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.NotificationSeverity;
import com.mirai.inventoryservice.models.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDTO {
    private UUID id;
    private NotificationType type;
    private NotificationSeverity severity;
    private String message;
    private UUID recipientId;
    private UUID itemId;
    private UUID inventoryId;
    private List<String> via;
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime resolvedAt;
}

