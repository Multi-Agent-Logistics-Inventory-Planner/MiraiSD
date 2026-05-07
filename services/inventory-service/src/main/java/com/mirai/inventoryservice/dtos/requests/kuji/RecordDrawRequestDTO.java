package com.mirai.inventoryservice.dtos.requests.kuji;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordDrawRequestDTO {
    @NotNull
    private UUID actorId;

    private String notes;

    @NotEmpty
    @Valid
    private List<DrawLine> draws;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrawLine {
        @NotNull
        private UUID tierId;

        @NotNull
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;
    }
}
