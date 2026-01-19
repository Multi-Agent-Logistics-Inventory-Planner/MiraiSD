package com.mirai.inventoryservice.exceptions;

public class RackNotFoundException extends RuntimeException {
    public RackNotFoundException(String message) {
        super(message);
    }
}

