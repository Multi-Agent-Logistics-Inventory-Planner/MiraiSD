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
  return useMutation<void, Error, { id: string; parentId?: string }>({
    mutationFn: ({ id }) => deleteProduct(id),
    onSuccess: async (_, { id, parentId }) => {
      try {
        // Remove deleted product's queries so we don't refetch and get 404
        qc.removeQueries({ queryKey: ["products", id, "with-children"] });
        qc.removeQueries({ queryKey: ["products", id] });

        // Optimistically update parent's with-children so the list updates immediately
        if (parentId) {
          const parentKey = ["products", parentId, "with-children"] as const;
          const prev = qc.getQueryData<Product>(parentKey);
          if (prev?.children) {
            const nextChildren = prev.children.filter((c) => c.id !== id);
            const totalChildStock = nextChildren.reduce(
              (sum, c) => sum + (c.quantity ?? 0),
              0
            );
            qc.setQueryData<Product>(parentKey, {
              ...prev,
              children: nextChildren,
              totalChildStock,
            });
          }
        }

        await qc.invalidateQueries({ queryKey: ["products"] });
        if (parentId) {
          await qc.invalidateQueries({
            queryKey: ["products", parentId, "with-children"],
          });
          await qc.invalidateQueries({
            queryKey: ["products", parentId, "children"],
          });
        }
      } catch {
        // Don't let cache updates block success; dialog still closes via mutate() onSuccess
      }
    },
  });
}

