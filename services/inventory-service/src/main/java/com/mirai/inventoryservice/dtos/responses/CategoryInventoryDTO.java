package com.mirai.inventoryservice.dtos.responses;

public record CategoryInventoryDTO(
    String category,
    Long totalItems,
    Integer totalStock
) {}
