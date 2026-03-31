package com.mirai.inventoryservice.exceptions;

public class StorageLocationNotFoundException extends RuntimeException {
    public StorageLocationNotFoundException(String message) {
        super(message);
    }
}
