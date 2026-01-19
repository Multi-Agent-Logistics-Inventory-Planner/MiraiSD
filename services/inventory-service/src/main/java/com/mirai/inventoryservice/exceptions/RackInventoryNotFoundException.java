package com.mirai.inventoryservice.exceptions;

public class RackInventoryNotFoundException extends RuntimeException {
    public RackInventoryNotFoundException(String message) {
        super(message);
    }
}

