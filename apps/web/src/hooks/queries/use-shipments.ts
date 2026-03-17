"use client";

import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { getShipmentsPaged, getDisplayStatusCounts, ShipmentFilters } from "@/lib/api/shipments";

export function useShipments(
  filters: ShipmentFilters = {},
  page: number = 0,
  size: number = 20
) {
  return useQuery({
    queryKey: ["shipments", filters, page, size],
    queryFn: () => getShipmentsPaged(filters, page, size),
    placeholderData: keepPreviousData,
  });
}

export function useShipmentDisplayStatusCounts() {
  return useQuery({
    queryKey: ["shipments", "display-status-counts"],
    queryFn: () => getDisplayStatusCounts(),
  });
}
