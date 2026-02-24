package com.mirai.inventoryservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReviewTrackingRequestDTO {
    private String canonicalName;
    private List<String> nameVariants;
    private Boolean isReviewTracked;
}
