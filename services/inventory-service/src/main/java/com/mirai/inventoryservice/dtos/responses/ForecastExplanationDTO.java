package com.mirai.inventoryservice.dtos.responses;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payload for the "Why this number" drawer on the predictions tab. Bundles
 * the forecast features JSONB (mu_hat, dow_multipliers, event_multipliers,
 * event_days_since, demand_regime, lead_time_source, etc.) with the most
 * recent restock timestamp so the UI can answer
 * "why is this prediction what it is?" without a second round-trip.
 */
public record ForecastExplanationDTO(
        UUID itemId,
        OffsetDateTime computedAt,
        Map<String, Object> features,
        OffsetDateTime lastRestockAt
) {}
