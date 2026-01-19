package com.mirai.inventoryservice.exceptions;

public class BoxBinNotFoundException extends RuntimeException {
    public BoxBinNotFoundException(String message) {
        super(message);
    }
}

