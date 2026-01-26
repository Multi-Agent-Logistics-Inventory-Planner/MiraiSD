"use client";

import { useQuery } from "@tanstack/react-query";
import { getShipmentsByProduct } from "@/lib/api/shipments";

export function useShipmentsByProduct(productId?: string | null) {
  return useQuery({
    queryKey: ["shipmentsByProduct", productId ?? "none"],
    queryFn: () => getShipmentsByProduct(productId as string),
    enabled: Boolean(productId),
  });
}
