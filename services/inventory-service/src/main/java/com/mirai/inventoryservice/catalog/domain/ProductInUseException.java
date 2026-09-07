package com.mirai.inventoryservice.catalog.domain;

public class ProductInUseException extends RuntimeException {
    public ProductInUseException(String message) {
        super(message);
    }
}
