package com.mirai.inventoryservice.exceptions;

/**
 * Thrown when a stock mutation would drop a LocationInventory row below the
 * quantity allocated to one or more OPEN kuji boxes at that location.
 */
public class KujiAllocationViolationException extends RuntimeException {
    public KujiAllocationViolationException(String message) {
        super(message);
    }
}
