package com.pm.inventoryservice.exceptions;

public class MachineNotFoundException extends RuntimeException {

    public MachineNotFoundException(String message) {
        super(message);
    }
}


