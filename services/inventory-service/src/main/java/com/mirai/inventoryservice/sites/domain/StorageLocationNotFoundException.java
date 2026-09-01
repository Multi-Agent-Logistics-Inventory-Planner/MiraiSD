package com.mirai.inventoryservice.sites.domain;

public class StorageLocationNotFoundException extends RuntimeException {
    public StorageLocationNotFoundException(String message) {
        super(message);
    }
}
