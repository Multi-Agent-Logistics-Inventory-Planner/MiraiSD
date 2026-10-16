package com.mirai.inventoryservice.shared.web;

/** Bounds client-supplied page sizes where a controller builds its own PageRequest. */
public final class PageLimits {

    public static final int MAX_PAGE_SIZE = 200;

    private PageLimits() {
    }

    public static int clampSize(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
