package com.mirai.inventoryservice.reviews.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDTO {
    private UUID id;
    private String externalId;
    private UUID userId;
    private String userName;
    private LocalDate reviewDate;
    private String reviewText;
    private Integer rating;
    private String reviewerName;
    private OffsetDateTime createdAt;
}
