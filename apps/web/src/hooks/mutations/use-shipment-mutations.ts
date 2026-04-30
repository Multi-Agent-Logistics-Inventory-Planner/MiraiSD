"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  createShipment,
  updateShipment,
  deleteShipment,
  receiveShipment,
  undoReceiveShipmentItem,
  undoReceiveShipmentItems,
  overrideShipmentStatus,
  type OverrideShipmentStatusRequest,
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
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
}

export function useUndoReceiveShipmentItemMutation() {
  const qc = useQueryClient();
  return useMutation<Shipment, Error, { shipmentId: string; itemId: string }>({
    mutationFn: ({ shipmentId, itemId }) =>
      undoReceiveShipmentItem(shipmentId, itemId),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["shipments"] });
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
}

export function useUndoReceiveShipmentItemsMutation() {
  const qc = useQueryClient();
  return useMutation<Shipment, Error, { shipmentId: string; itemIds: string[] }>({
    mutationFn: ({ shipmentId, itemIds }) =>
      undoReceiveShipmentItems(shipmentId, itemIds),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["shipments"] });
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
}

export function useOverrideShipmentStatusMutation() {
  const qc = useQueryClient();
  return useMutation<Shipment, Error, { id: string; payload: OverrideShipmentStatusRequest }>({
    mutationFn: ({ id, payload }) => overrideShipmentStatus(id, payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["shipments"] });
    },
  });
}
