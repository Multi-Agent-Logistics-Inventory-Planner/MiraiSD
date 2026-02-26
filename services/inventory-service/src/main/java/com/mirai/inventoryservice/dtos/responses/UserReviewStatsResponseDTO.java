package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReviewStatsResponseDTO {
    private UUID userId;
    private String userName;

    // All-time stats
    private Integer allTimeReviewCount;
    private LocalDate firstReviewDate;
    private LocalDate lastReviewDate;

    // Selected month stats
    private Integer selectedMonthReviewCount;

    // Ranking
    private Integer allTimeRank;
}
