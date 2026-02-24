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
public class ReviewSummaryResponseDTO {
    // Legacy fields for backward compatibility
    private UUID employeeId;
    private String employeeName;

    // New user-based fields
    private UUID userId;
    private String userName;

    private Integer totalReviews;
    private Double averageReviewsPerDay;
    private LocalDate lastReviewDate;
}
