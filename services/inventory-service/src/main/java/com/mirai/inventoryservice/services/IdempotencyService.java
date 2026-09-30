package com.mirai.inventoryservice.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-(site, user, key) idempotency cache. Single-replica deployment assumption — if you
 * scale horizontally, swap this for a Redis-backed implementation.
 *
 * The lootbox_plays table also has UNIQUE(site_id, user_id, idempotency_key) (V79) as a
 * database-level safety net for replays that miss the cache (e.g. after a restart within 5
 * minutes).
 *
 * siteId is part of the cache key, not just an extra check on the cached value, so that reusing
 * the same key at a different site within the TTL can never return another site's cached prize
 * and balance (review finding P1 for .specs/phase-7-kuji-lootbox: the key was previously
 * user+key only).
 */
@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final long MAX_ENTRIES = 10_000;

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_ENTRIES)
            .build();

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(UUID siteId, UUID userId, String key, Class<T> type) {
        if (key == null || key.isBlank()) return Optional.empty();
        Object cached = cache.getIfPresent(cacheKey(siteId, userId, key));
        if (cached == null || !type.isInstance(cached)) return Optional.empty();
        return Optional.of((T) cached);
    }

    public void put(UUID siteId, UUID userId, String key, Object value) {
        if (key == null || key.isBlank() || value == null) return;
        cache.put(cacheKey(siteId, userId, key), value);
    }

    private String cacheKey(UUID siteId, UUID userId, String key) {
        return siteId + ":" + userId + ":" + key;
    }
}
