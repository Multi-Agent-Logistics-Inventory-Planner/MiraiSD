package com.mirai.inventoryservice.models.enums;

public enum StockMovementReason {
    INITIAL_STOCK,
    RESTOCK,
    SHIPMENT_RECEIPT,
    SHIPMENT_RECEIPT_REVERSED,
    SHIPMENT_PARTIAL_RECEIPT,
    SHIPMENT_EDITED,
    SHIPMENT_DELETED,
    SALE,
    DAMAGE,
    ADJUSTMENT,
    RETURN,
    TRANSFER,
    REMOVED,
    DISPLAY_SET,
    DISPLAY_REMOVED,
    DISPLAY_SWAP
}

