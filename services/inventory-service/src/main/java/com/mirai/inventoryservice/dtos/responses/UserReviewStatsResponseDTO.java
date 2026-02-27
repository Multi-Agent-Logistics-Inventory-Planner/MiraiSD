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
    /** Total reviews across all users in the selected month (null if no month selected). */
    private Integer selectedMonthTotalReviews;
    /** This user's share of reviews in the selected month, 0–100 (null if no month or no reviews). */
    private Double selectedMonthPercentage;

    // Ranking
    private Integer allTimeRank;
}
