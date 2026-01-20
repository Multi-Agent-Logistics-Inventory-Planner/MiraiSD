"use client";

import { useQuery } from "@tanstack/react-query";
import { getShipments } from "@/lib/api/shipments";
import type { ShipmentStatus } from "@/types/api";

export function useShipments(status?: ShipmentStatus) {
  return useQuery({
    queryKey: ["shipments", status ?? "ALL"],
    queryFn: () => getShipments(status),
  });
}

