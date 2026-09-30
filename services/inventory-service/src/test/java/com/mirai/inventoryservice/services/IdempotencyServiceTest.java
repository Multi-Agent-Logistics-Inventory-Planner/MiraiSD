package com.mirai.inventoryservice.services;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the .specs/phase-7-kuji-lootbox review finding P1: the cache key was
 * previously user+key only, so replaying the same idempotency key at a second site within the
 * 5-minute TTL returned the first site's cached prize/balance without ever executing a play at
 * the second site.
 */
class IdempotencyServiceTest {

    private final IdempotencyService service = new IdempotencyService();

    @Test
    void sameUserAndKeyAtDifferentSites_doNotShareACacheEntry() {
        UUID userId = UUID.randomUUID();
        UUID siteA = UUID.randomUUID();
        UUID siteB = UUID.randomUUID();
        String key = "same-idempotency-key";

        service.put(siteA, userId, key, "site-a-result");

        assertThat(service.get(siteA, userId, key, String.class)).contains("site-a-result");
        assertThat(service.get(siteB, userId, key, String.class)).isEmpty();
    }

    @Test
    void puttingAtSiteBAfterSiteA_doesNotOverwriteSiteAsEntry() {
        UUID userId = UUID.randomUUID();
        UUID siteA = UUID.randomUUID();
        UUID siteB = UUID.randomUUID();
        String key = "same-idempotency-key";

        service.put(siteA, userId, key, "site-a-result");
        service.put(siteB, userId, key, "site-b-result");

        assertThat(service.get(siteA, userId, key, String.class)).contains("site-a-result");
        assertThat(service.get(siteB, userId, key, String.class)).contains("site-b-result");
    }

    @Test
    void sameSiteAndKeyButDifferentUsers_doNotShareACacheEntry() {
        UUID siteId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String key = "same-idempotency-key";

        service.put(siteId, userA, key, "user-a-result");

        assertThat(service.get(siteId, userA, key, String.class)).contains("user-a-result");
        assertThat(service.get(siteId, userB, key, String.class)).isEmpty();
    }

    @Test
    void blankOrNullKey_neverCachesOrReturnsAnything() {
        UUID siteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.put(siteId, userId, "", "value");
        service.put(siteId, userId, null, "value");

        assertThat(service.get(siteId, userId, "", String.class)).isEmpty();
        assertThat(service.get(siteId, userId, null, String.class)).isEmpty();
    }
}
