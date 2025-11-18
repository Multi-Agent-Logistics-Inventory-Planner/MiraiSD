package com.mirai.inventoryservice.exceptions;

public class CabinetNotFoundException extends RuntimeException {
    public CabinetNotFoundException(String message) {
        super(message);
    }
}

