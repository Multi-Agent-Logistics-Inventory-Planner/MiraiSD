"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  createSupplier,
  updateSupplier,
  deleteSupplier,
  bulkAssignProducts,
} from "@/lib/api/suppliers";
import { supplierKeys } from "@/hooks/queries/use-suppliers";
import type { Supplier, SupplierRequest } from "@/types/api";

export function useCreateSupplierMutation() {
  const qc = useQueryClient();
  return useMutation<Supplier, Error, SupplierRequest>({
    mutationFn: (payload) => createSupplier(payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: supplierKeys.all });
    },
  });
}

export function useUpdateSupplierMutation() {
  const qc = useQueryClient();
  return useMutation<Supplier, Error, { id: string; payload: SupplierRequest }>({
    mutationFn: ({ id, payload }) => updateSupplier(id, payload),
    onSuccess: async (_, { id }) => {
      await qc.invalidateQueries({ queryKey: supplierKeys.all });
      await qc.invalidateQueries({ queryKey: supplierKeys.detail(id) });
    },
  });
}

export function useDeleteSupplierMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteSupplier(id),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: supplierKeys.all });
    },
  });
}

export function useBulkAssignProductsMutation() {
  const qc = useQueryClient();
  return useMutation<number, Error, { supplierId: string; productIds: string[] }>({
    mutationFn: ({ supplierId, productIds }) =>
      bulkAssignProducts(supplierId, productIds),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: supplierKeys.all });
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
}
