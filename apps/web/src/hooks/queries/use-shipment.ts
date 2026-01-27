"use client";

import { useQuery } from "@tanstack/react-query";
import { getShipmentById } from "@/lib/api/shipments";

export function useShipment(id: string | undefined) {
  return useQuery({
    queryKey: ["shipment", id],
    queryFn: () => getShipmentById(id!),
    enabled: !!id,
  });
}
