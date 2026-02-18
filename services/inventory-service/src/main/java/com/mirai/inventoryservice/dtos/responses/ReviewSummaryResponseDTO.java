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
    private UUID employeeId;
    private String employeeName;
    private Integer totalReviews;
    private Double averageReviewsPerDay;
    private LocalDate lastReviewDate;
}
