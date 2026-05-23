package com.mirai.inventoryservice.models.enums;

public enum StockMovementReason {
    INITIAL_STOCK,
    RESTOCK,
    SHIPMENT_RECEIPT,
    SHIPMENT_RECEIPT_REVERSED,
    SHIPMENT_PARTIAL_RECEIPT,
    SHIPMENT_EDITED,
    SHIPMENT_DELETED,
    SHIPMENT_STATUS_OVERRIDDEN,
    SALE,
    DAMAGE,
    ADJUSTMENT,
    RETURN,
    TRANSFER,
    REMOVED,
    DISPLAY_SET,
    DISPLAY_REMOVED,
    DISPLAY_SWAP,
    KUJI_PRIZE_WON,
    KUJI_DRAW_REVERSED,
    KUJI_SLIP_ADJUSTMENT,
    // Lootbox / coin-economy admin events (no stock movements). The enum is the
    // shared audit-log "reason" vocabulary; keeping these here avoids a second
    // enum + DB column rename.
    COIN_RATE_CHANGED
}

