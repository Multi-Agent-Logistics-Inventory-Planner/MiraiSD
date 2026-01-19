package com.mirai.inventoryservice.exceptions;

public class CabinetInventoryNotFoundException extends RuntimeException {
    public CabinetInventoryNotFoundException(String message) {
        super(message);
    }
}

