package com.mirai.inventoryservice.sites.domain;

public class DuplicateLocationCodeException extends RuntimeException {
    public DuplicateLocationCodeException(String message) {
        super(message);
    }
}
