package com.mirai.inventoryservice.exceptions;

public class DuplicateLocationCodeException extends RuntimeException {
    public DuplicateLocationCodeException(String message) {
        super(message);
    }
}
