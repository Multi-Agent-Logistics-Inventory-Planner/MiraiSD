"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import {
  createSiteShipment,
  updateSiteShipment,
  deleteSiteShipment,
  receiveSiteShipment,
  undoReceiveSiteShipmentItem,
  undoReceiveSiteShipmentItems,
  overrideSiteShipmentStatus,
} from "@/lib/api/site-shipments";
import type { OverrideShipmentStatusRequest } from "@/lib/api/shipments";
import type {
  Shipment,
  ShipmentRequest,
  ReceiveShipmentRequest,
} from "@/types/api";

// --- Site-scoped shipment mutations (.specs/phase-7-shipments T-5, AC-5) -----------------------
// siteId is captured from useCurrentSite() at the hook level and threaded through each
// mutationFn's variables, mirroring use-location-mutations.ts's pattern - a mutation attempted
// with no resolved site throws rather than silently hitting a stale/undefined site path.

export function useCreateShipmentMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const mutation = useMutation<
    Shipment,
    Error,
    { payload: ShipmentRequest; siteId: string | undefined }
  >({
    mutationFn: ({ payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return createSiteShipment(originSiteId, payload);
    },
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["shipments", variables.siteId] });
    },
  });
  return {
    ...mutation,
    mutate: (payload: ShipmentRequest, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ payload, siteId }, options),
    mutateAsync: (payload: ShipmentRequest, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ payload, siteId }, options),
  };
}

export function useUpdateShipmentMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const mutation = useMutation<
    Shipment,
    Error,
    { id: string; payload: ShipmentRequest; siteId: string | undefined }
  >({
    mutationFn: ({ id, payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return updateSiteShipment(originSiteId, id, payload);
    },
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["shipments", variables.siteId] });
    },
  });
  return {
    ...mutation,
    mutate: (
      variables: { id: string; payload: ShipmentRequest },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { id: string; payload: ShipmentRequest },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}

export function useDeleteShipmentMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const mutation = useMutation<void, Error, { id: string; siteId: string | undefined }>({
    mutationFn: ({ id, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return deleteSiteShipment(originSiteId, id);
    },
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["shipments", variables.siteId] });
    },
  });
  return {
    ...mutation,
    mutate: (variables: { id: string }, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { id: string },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}

export function useReceiveShipmentMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const mutation = useMutation<
    Shipment,
    Error,
    { id: string; payload: ReceiveShipmentRequest; siteId: string | undefined }
  >({
    mutationFn: ({ id, payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return receiveSiteShipment(originSiteId, id, payload);
    },
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["shipments", variables.siteId] });
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
  return {
    ...mutation,
    mutate: (
      variables: { id: string; payload: ReceiveShipmentRequest },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { id: string; payload: ReceiveShipmentRequest },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}

export function useUndoReceiveShipmentItemMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const mutation = useMutation<
    Shipment,
    Error,
    { shipmentId: string; itemId: string; siteId: string | undefined }
  >({
    mutationFn: ({ shipmentId, itemId, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return undoReceiveSiteShipmentItem(originSiteId, shipmentId, itemId);
    },
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["shipments", variables.siteId] });
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
  return {
    ...mutation,
    mutate: (
      variables: { shipmentId: string; itemId: string },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { shipmentId: string; itemId: string },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}

export function useUndoReceiveShipmentItemsMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const mutation = useMutation<
    Shipment,
    Error,
    { shipmentId: string; itemIds: string[]; siteId: string | undefined }
  >({
    mutationFn: ({ shipmentId, itemIds, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return undoReceiveSiteShipmentItems(originSiteId, shipmentId, itemIds);
    },
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["shipments", variables.siteId] });
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
  return {
    ...mutation,
    mutate: (
      variables: { shipmentId: string; itemIds: string[] },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { shipmentId: string; itemIds: string[] },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}

export function useOverrideShipmentStatusMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const mutation = useMutation<
    Shipment,
    Error,
    { id: string; payload: OverrideShipmentStatusRequest; siteId: string | undefined }
  >({
    mutationFn: ({ id, payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return overrideSiteShipmentStatus(originSiteId, id, payload);
    },
    onSuccess: async (_data, variables) => {
      await qc.invalidateQueries({ queryKey: ["shipments", variables.siteId] });
    },
  });
  return {
    ...mutation,
    mutate: (
      variables: { id: string; payload: OverrideShipmentStatusRequest },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { id: string; payload: OverrideShipmentStatusRequest },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}
