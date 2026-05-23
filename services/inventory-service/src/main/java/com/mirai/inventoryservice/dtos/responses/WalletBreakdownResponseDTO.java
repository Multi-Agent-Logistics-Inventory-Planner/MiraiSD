package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * Wallet breakdown for the "X coins expiring on Y" UI. Groups upcoming expirations
 * (within the next 30 days) by date so users see a clear timeline.
 */
@Builder
public record WalletBreakdownResponseDTO(
        long total,
        List<ExpirationBucket> expiringSoon,
        LocalDate nextExpiryDate
) {
    @Builder
    public record ExpirationBucket(int amount, LocalDate expiresOn) {}
}
