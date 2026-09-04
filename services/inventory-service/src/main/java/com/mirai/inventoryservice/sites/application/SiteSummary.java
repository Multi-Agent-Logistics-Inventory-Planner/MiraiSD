package com.mirai.inventoryservice.sites.application;

import java.util.UUID;

/**
 * Read-only site projection exposed to other modules through {@link SiteDirectory}, so callers
 * outside {@code sites} never depend on {@code sites.domain.Site} directly.
 */
public record SiteSummary(UUID id, String name, String code) {
}
