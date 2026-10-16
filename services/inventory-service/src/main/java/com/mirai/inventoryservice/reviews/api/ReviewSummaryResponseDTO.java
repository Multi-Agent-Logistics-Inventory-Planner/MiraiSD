package com.mirai.inventoryservice.reviews.api;

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
public class ReviewSummaryResponseDTO {
    private UUID userId;
    private String userName;
    private Integer totalReviews;
    private Double averageReviewsPerDay;
    private LocalDate lastReviewDate;
}
