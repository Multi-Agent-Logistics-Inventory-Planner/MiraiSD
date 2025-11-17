package com.pm.inventoryservice.exceptions;

public class BinNotFoundException extends RuntimeException {

    public BinNotFoundException(String message) {
        super(message);
    }
}


