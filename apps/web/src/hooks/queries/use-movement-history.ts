"use client";

import { useQuery } from "@tanstack/react-query";
import { getStockMovementHistory } from "@/lib/api/stock-movements";

export function useMovementHistory(
  productId?: string | null,
  page: number = 0,
  size: number = 20
) {
  return useQuery({
    queryKey: ["movementHistory", productId ?? "none", page, size],
    queryFn: () => getStockMovementHistory(productId as string, page, size),
    enabled: Boolean(productId),
  });
}
