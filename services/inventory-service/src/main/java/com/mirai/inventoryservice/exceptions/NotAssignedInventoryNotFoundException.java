package com.mirai.inventoryservice.exceptions;

public class NotAssignedInventoryNotFoundException extends RuntimeException {
    public NotAssignedInventoryNotFoundException(String message) {
        super(message);
    }
}
