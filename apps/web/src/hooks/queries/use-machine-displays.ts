"use client";

import { useMemo } from "react";
import { useQuery, keepPreviousData, skipToken } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import {
  getSiteActiveDisplays,
  getSiteActiveDisplaysByType,
  getSiteMachineHistoryPaged,
  getSiteProductDisplayHistory,
  getSiteActiveDisplaysForMachine,
} from "@/lib/api/site-machine-displays";
import { LocationType, MachineDisplay } from "@/types/api";

/** Displays older than this threshold are considered stale */
export const STALE_DISPLAY_THRESHOLD_DAYS = 45;

/**
 * Fetch all active machine displays at the current site
 */
export function useActiveDisplays() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["machine-displays", siteId, "active"],
    queryFn: siteId ? () => getSiteActiveDisplays(siteId) : skipToken,
    staleTime: 10 * 60 * 1000,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}

/**
 * Fetch active displays for a specific location type at the current site
 */
export function useActiveDisplaysByType(locationType?: LocationType) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["machine-displays", siteId, "active", "by-type", locationType],
    queryFn:
      siteId && locationType ? () => getSiteActiveDisplaysByType(siteId, locationType) : skipToken,
    staleTime: 10 * 60 * 1000,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}

/**
 * Fetch display history for a machine at the current site with pagination
 */
export function useMachineDisplayHistoryPaged(
  locationType?: LocationType,
  machineId?: string,
  page: number = 0,
  size: number = 10
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["machine-displays", siteId, "history", "paged", locationType, machineId, page, size],
    queryFn:
      siteId && locationType && machineId
        ? () => getSiteMachineHistoryPaged(siteId, locationType, machineId, page, size)
        : skipToken,
    placeholderData: keepPreviousData,
    staleTime: 10 * 60 * 1000,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}

/**
 * Fetch display history for a product at the current site
 */
export function useProductDisplayHistory(productId?: string) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["machine-displays", siteId, "product-history", productId],
    queryFn: siteId && productId ? () => getSiteProductDisplayHistory(siteId, productId) : skipToken,
    staleTime: 10 * 60 * 1000,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}

/**
 * Fetch all active displays for a specific machine at the current site
 */
export function useActiveDisplaysForMachine(
  locationType?: LocationType,
  machineId?: string
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["machine-displays", siteId, "machine-active", locationType, machineId],
    queryFn:
      siteId && locationType && machineId
        ? () => getSiteActiveDisplaysForMachine(siteId, locationType, machineId)
        : skipToken,
    staleTime: 10 * 60 * 1000,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}

/**
 * Summary of displays grouped by machine for longest running analysis
 */
export interface MachineDisplaySummary {
  locationType: LocationType;
  machineId: string;
  machineCode: string;
  productCount: number;
  maxDaysActive: number;
  oldestDisplayStartedAt: string;
  hasStaleDisplays: boolean;
  products: MachineDisplay[];
}

/**
 * Fetch longest running displays grouped by machine
 * Uses useActiveDisplays internally to share cache
 */
export function useLongestRunningDisplays(options?: {
  locationType?: LocationType;
  staleThresholdDays?: number;
}) {
  const {
    locationType,
    staleThresholdDays = STALE_DISPLAY_THRESHOLD_DAYS,
  } = options ?? {};
  const { data: displays, isLoading, isError, error } = useActiveDisplays();

  const summaries = useMemo<MachineDisplaySummary[]>(() => {
    if (!displays) return [];

    // Group by machine key (O(n) algorithm using mutable local Map)
    const grouped = new Map<string, MachineDisplay[]>();
    for (const display of displays) {
      // Filter by location type if specified
      if (locationType && display.locationType !== locationType) continue;

      const key = `${display.locationType}:${display.machineId}`;
      const existing = grouped.get(key);
      if (existing) {
        existing.push(display);
      } else {
        grouped.set(key, [display]);
      }
    }

    // Transform to summaries
    const result: MachineDisplaySummary[] = [];
    for (const [, machineDisplays] of grouped) {
      if (machineDisplays.length === 0) continue;

      const first = machineDisplays[0];
      const maxDaysActive = Math.max(...machineDisplays.map((d) => d.daysActive));
      const oldestDisplay = machineDisplays.reduce((oldest, current) =>
        new Date(current.startedAt) < new Date(oldest.startedAt)
          ? current
          : oldest
      );
      const hasStaleDisplays = machineDisplays.some(
        (d) => d.daysActive >= staleThresholdDays
      );

      result.push({
        locationType: first.locationType,
        machineId: first.machineId,
        machineCode: first.machineCode,
        productCount: machineDisplays.length,
        maxDaysActive,
        oldestDisplayStartedAt: oldestDisplay.startedAt,
        hasStaleDisplays,
        products: machineDisplays,
      });
    }

    // Filter to only include machines with stale displays (>= threshold)
    // Sort by maxDaysActive descending
    return result
      .filter((s) => s.maxDaysActive >= staleThresholdDays)
      .sort((a, b) => b.maxDaysActive - a.maxDaysActive);
  }, [displays, locationType, staleThresholdDays]);

  return {
    data: summaries,
    isLoading,
    isError,
    error,
  };
}
