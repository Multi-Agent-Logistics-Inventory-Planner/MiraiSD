"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createProduct, updateProduct, deleteProduct } from "@/lib/api/products";
import type { Product, ProductRequest } from "@/types/api";

export function useCreateProductMutation() {
  const qc = useQueryClient();
  return useMutation<Product, Error, ProductRequest>({
    mutationFn: (payload) => createProduct(payload),
    onSuccess: async (newProduct) => {
      // Invalidate all product queries (list, with-children, children)
      await qc.invalidateQueries({ queryKey: ["products"] });
      // Also invalidate the parent's children query if this is a child product
      if (newProduct.parentId) {
        await qc.invalidateQueries({
          queryKey: ["products", newProduct.parentId, "with-children"],
        });
        await qc.invalidateQueries({
          queryKey: ["products", newProduct.parentId, "children"],
        });
      }
    },
  });
}

export function useUpdateProductMutation() {
  const qc = useQueryClient();
  return useMutation<Product, Error, { id: string; payload: ProductRequest }>({
    mutationFn: ({ id, payload }) => updateProduct(id, payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
}

export function useDeleteProductMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteProduct(id),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["products"] });
    },
  });
}

