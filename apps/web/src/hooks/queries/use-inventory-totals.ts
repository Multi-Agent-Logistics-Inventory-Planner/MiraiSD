"use client";

import { useQuery } from "@tanstack/react-query";
import { getInventoryTotalsByItemId } from "@/lib/api/dashboard";

export function useInventoryTotals() {
  return useQuery({
    queryKey: ["inventoryTotals"],
    queryFn: getInventoryTotalsByItemId,
    staleTime: 60 * 1000,
  });
}

