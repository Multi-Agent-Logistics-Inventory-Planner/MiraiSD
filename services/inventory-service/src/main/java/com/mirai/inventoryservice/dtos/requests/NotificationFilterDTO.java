package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationFilterDTO {
    private String search;
    private NotificationType type;
    private Boolean resolved;
    private OffsetDateTime fromDate;
    private OffsetDateTime toDate;
}
