package com.mirai.inventoryservice.catalog.domain;

public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(String message) {
        super(message);
    }
}
