import { useQuery, keepPreviousData } from "@tanstack/react-query";
import {
  getActiveDisplays,
  getActiveDisplaysPaged,
  getActiveDisplaysByType,
  getStaleDisplays,
  getMachineHistory,
  getMachineHistoryPaged,
  getProductDisplayHistory,
  getActiveDisplaysForMachine,
} from "@/lib/api/machine-displays";
import { LocationType } from "@/types/api";

/**
 * Fetch all active machine displays
 */
export function useActiveDisplays() {
  return useQuery({
    queryKey: ["machine-displays", "active"],
    queryFn: getActiveDisplays,
    staleTime: 30_000,
  });
}

/**
 * Fetch active displays with pagination
 */
export function useActiveDisplaysPaged(page: number = 0, size: number = 20) {
  return useQuery({
    queryKey: ["machine-displays", "active", "paged", page, size],
    queryFn: () => getActiveDisplaysPaged(page, size),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
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
    staleTime: 30_000,
  });
}

/**
 * Fetch stale displays (configurable threshold)
 */
export function useStaleDisplays(thresholdDays?: number) {
  return useQuery({
    queryKey: ["machine-displays", "stale", thresholdDays],
    queryFn: () => getStaleDisplays(thresholdDays),
    staleTime: 30_000,
  });
}

/**
 * Fetch display history for a machine
 */
export function useMachineDisplayHistory(
  locationType?: LocationType,
  machineId?: string
) {
  return useQuery({
    queryKey: ["machine-displays", "history", locationType, machineId],
    queryFn: () => getMachineHistory(locationType!, machineId!),
    enabled: !!locationType && !!machineId,
    staleTime: 30_000,
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
    staleTime: 30_000,
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
    staleTime: 30_000,
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
    staleTime: 30_000,
  });
}
