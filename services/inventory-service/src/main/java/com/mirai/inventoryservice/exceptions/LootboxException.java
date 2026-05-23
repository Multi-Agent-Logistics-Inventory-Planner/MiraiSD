package com.mirai.inventoryservice.exceptions;

/**
 * Business-rule violations for the lootbox feature: insufficient balance, invalid tier
 * percentages, attempts to deactivate the last prize in the last active tier, etc.
 * Mapped to HTTP 400 by GlobalExceptionHandler.
 */
public class LootboxException extends RuntimeException {
    public LootboxException(String message) {
        super(message);
    }
}
