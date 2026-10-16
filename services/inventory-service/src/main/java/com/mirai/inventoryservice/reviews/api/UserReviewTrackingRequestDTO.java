package com.mirai.inventoryservice.reviews.api;

import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReviewTrackingRequestDTO {
    @Size(max = 100)
    private String canonicalName;

    @Size(max = 50)
    private List<@Size(max = 100) String> nameVariants;

    private Boolean isReviewTracked;
}
