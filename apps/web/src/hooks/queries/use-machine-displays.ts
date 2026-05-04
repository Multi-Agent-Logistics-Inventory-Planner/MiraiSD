"use client";

import { useMemo } from "react";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import {
  getActiveDisplays,
  getActiveDisplaysByType,
  getMachineHistoryPaged,
  getProductDisplayHistory,
  getActiveDisplaysForMachine,
} from "@/lib/api/machine-displays";
import { LocationType, MachineDisplay } from "@/types/api";

/** Displays older than this threshold are considered stale */
export const STALE_DISPLAY_THRESHOLD_DAYS = 45;

/**
 * Fetch all active machine displays
 */
export function useActiveDisplays() {
  return useQuery({
    queryKey: ["machine-displays", "active"],
    queryFn: getActiveDisplays,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Fetch active displays for a specific location type
 */
export function useActiveDisplaysByType(locationType?: LocationType) {
  return useQuery({
    queryKey: ["machine-displays", "active", "by-type", locationType],
    queryFn: () => getActiveDisplaysByType(locationType!),
    enabled: !!locationType,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Fetch display history for a machine with pagination
 */
export function useMachineDisplayHistoryPaged(
  locationType?: LocationType,
  machineId?: string,
  page: number = 0,
  size: number = 10
) {
  return useQuery({
    queryKey: ["machine-displays", "history", "paged", locationType, machineId, page, size],
    queryFn: () => getMachineHistoryPaged(locationType!, machineId!, page, size),
    enabled: !!locationType && !!machineId,
    placeholderData: keepPreviousData,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Fetch display history for a product
 */
export function useProductDisplayHistory(productId?: string) {
  return useQuery({
    queryKey: ["machine-displays", "product-history", productId],
    queryFn: () => getProductDisplayHistory(productId!),
    enabled: !!productId,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Fetch all active displays for a specific machine
 */
export function useActiveDisplaysForMachine(
  locationType?: LocationType,
  machineId?: string
) {
  return useQuery({
    queryKey: ["machine-displays", "machine-active", locationType, machineId],
    queryFn: () => getActiveDisplaysForMachine(locationType!, machineId!),
    enabled: !!locationType && !!machineId,
    staleTime: 10 * 60 * 1000,
  });
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
