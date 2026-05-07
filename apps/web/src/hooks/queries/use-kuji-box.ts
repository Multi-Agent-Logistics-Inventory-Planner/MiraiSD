"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getActiveKujiBox,
  getKujiAllocationsByLocation,
  getKujiAllocationsByProduct,
  getKujiBox,
  getKujiBoxHistory,
  getLastClosedKujiTiers,
} from "@/lib/api/kuji-boxes";
import { ApiClientError } from "@/lib/api/client";

export function useActiveKujiBox(productId: string | null | undefined) {
  return useQuery({
    queryKey: ["kuji-box", "active", productId],
    queryFn: () => getActiveKujiBox(productId!),
    enabled: !!productId,
    retry: (_, error) =>
      !(error instanceof ApiClientError && error.status === 404),
  });
}

export function useKujiBoxHistory(productId: string | null | undefined) {
  return useQuery({
    queryKey: ["kuji-box", "history", productId],
    queryFn: () => getKujiBoxHistory(productId!),
    enabled: !!productId,
  });
}

export function useLastClosedKujiTiers(productId: string | null | undefined) {
  return useQuery({
    queryKey: ["kuji-box", "last-tiers", productId],
    queryFn: () => getLastClosedKujiTiers(productId!),
    enabled: !!productId,
  });
}

export function useKujiBox(boxId: string | null | undefined) {
  return useQuery({
    queryKey: ["kuji-box", "detail", boxId],
    queryFn: () => getKujiBox(boxId!),
    enabled: !!boxId,
    retry: (_, error) =>
      !(error instanceof ApiClientError && error.status === 404),
  });
}

export function useKujiAllocationsByLocation(
  locationId: string | null | undefined
) {
  return useQuery({
    queryKey: ["kuji-box", "allocations", "by-location", locationId],
    queryFn: () => getKujiAllocationsByLocation(locationId!),
    enabled: !!locationId,
  });
}

export function useKujiAllocationsByProduct(
  productId: string | null | undefined
) {
  return useQuery({
    queryKey: ["kuji-box", "allocations", "by-product", productId],
    queryFn: () => getKujiAllocationsByProduct(productId!),
    enabled: !!productId,
  });
}
