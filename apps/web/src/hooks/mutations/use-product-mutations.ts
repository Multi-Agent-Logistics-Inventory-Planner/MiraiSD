"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createProduct, updateProduct, deleteProduct } from "@/lib/api/products";
import type { Product, ProductRequest } from "@/types/api";

export function useCreateProductMutation() {
  const qc = useQueryClient();
  return useMutation<Product, Error, ProductRequest>({
    mutationFn: (payload) => createProduct(payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["products"] });
      await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });
    },
  });
}

export function useUpdateProductMutation() {
  const qc = useQueryClient();
  return useMutation<Product, Error, { id: string; payload: ProductRequest }>({
    mutationFn: ({ id, payload }) => updateProduct(id, payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["products"] });
      await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });
    },
  });
}

export function useDeleteProductMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteProduct(id),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["products"] });
      await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });
    },
  });
}

