package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentLootboxPlayResponseDTO {
    private UUID id;
    private UUID prizeId;
    private String prizeName;
    private String prizeImageUrl;
    private String tierName;
    private String tierColor;
    private String userDisplay;
    private OffsetDateTime playedAt;
}
