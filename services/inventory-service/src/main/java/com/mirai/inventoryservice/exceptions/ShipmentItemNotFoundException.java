package com.mirai.inventoryservice.exceptions;

public class ShipmentItemNotFoundException extends RuntimeException {
    public ShipmentItemNotFoundException(String message) {
        super(message);
    }
}
