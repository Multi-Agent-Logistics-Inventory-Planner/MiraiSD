package com.mirai.inventoryservice.catalog.application;

import java.util.Set;
import java.util.UUID;

/**
 * Reports which products currently have an open Kuji box, so product list views can annotate
 * {@code hasActiveBox}.
 *
 * <p>Declared here so {@code catalog} does not depend on {@code kuji}'s repository directly;
 * implemented outside {@code catalog}. See docs: .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface OpenKujiBoxPort {

    /** Returns the IDs of every product that is the parent of a currently OPEN Kuji box. */
    Set<UUID> findProductIdsWithOpenBox();
}
