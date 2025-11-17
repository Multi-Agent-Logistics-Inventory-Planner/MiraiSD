package com.pm.inventoryservice.exceptions;

public class MachineInventoryNotFoundException extends RuntimeException {
    public MachineInventoryNotFoundException(String message) {
        super(message);
    }
}

