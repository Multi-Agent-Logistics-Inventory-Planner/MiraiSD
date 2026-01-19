package com.mirai.inventoryservice.exceptions;

public class InvalidShipmentStatusException extends RuntimeException {
    public InvalidShipmentStatusException(String message) {
        super(message);
    }
}
