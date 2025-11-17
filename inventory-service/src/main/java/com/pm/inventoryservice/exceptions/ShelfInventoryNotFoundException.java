package com.pm.inventoryservice.exceptions;

public class ShelfInventoryNotFoundException extends RuntimeException {
    public ShelfInventoryNotFoundException(String message) {
        super(message);
    }
}

