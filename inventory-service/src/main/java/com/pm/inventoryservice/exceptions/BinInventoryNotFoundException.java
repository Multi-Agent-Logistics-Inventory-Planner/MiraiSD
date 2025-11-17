package com.pm.inventoryservice.exceptions;

public class BinInventoryNotFoundException extends RuntimeException {
    public BinInventoryNotFoundException(String message) {
        super(message);
    }
}

