"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  createShipment,
  updateShipment,
  deleteShipment,
  receiveShipment,
} from "@/lib/api/shipments";
import type {
  Shipment,
  ShipmentRequest,
  ReceiveShipmentRequest,
} from "@/types/api";

export function useCreateShipmentMutation() {
  const qc = useQueryClient();
  return useMutation<Shipment, Error, ShipmentRequest>({
    mutationFn: (payload) => createShipment(payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["shipments"] });
    },
  });
}

export function useUpdateShipmentMutation() {
  const qc = useQueryClient();
  return useMutation<Shipment, Error, { id: string; payload: ShipmentRequest }>({
    mutationFn: ({ id, payload }) => updateShipment(id, payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["shipments"] });
    },
  });
}

export function useDeleteShipmentMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteShipment(id),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["shipments"] });
    },
  });
}

export function useReceiveShipmentMutation() {
  const qc = useQueryClient();
  return useMutation<
    Shipment,
    Error,
    { id: string; payload: ReceiveShipmentRequest }
  >({
    mutationFn: ({ id, payload }) => receiveShipment(id, payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["shipments"] });
      await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });
    },
  });
}
