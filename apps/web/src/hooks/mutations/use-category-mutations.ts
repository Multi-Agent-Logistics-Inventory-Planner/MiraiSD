"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  createCategory,
  updateCategory,
  deleteCategory,
} from "@/lib/api/categories";
import type { Category, CategoryRequest } from "@/types/api";

export function useCreateCategoryMutation() {
  const qc = useQueryClient();
  return useMutation<Category, Error, CategoryRequest>({
    mutationFn: (payload) => createCategory(payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["categories"] });
    },
  });
}

export function useUpdateCategoryMutation() {
  const qc = useQueryClient();
  return useMutation<Category, Error, { id: string; payload: CategoryRequest }>({
    mutationFn: ({ id, payload }) => updateCategory(id, payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["categories"] });
    },
  });
}

export function useDeleteCategoryMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteCategory(id),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["categories"] });
    },
  });
}

/**
 * Create a child category (subcategory) under a parent.
 * This is just createCategory with a parentId.
 */
export function useCreateChildCategoryMutation() {
  const qc = useQueryClient();
  return useMutation<
    Category,
    Error,
    { parentId: string; name: string }
  >({
    mutationFn: ({ parentId, name }) => createCategory({ name, parentId }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["categories"] });
    },
  });
}

/**
 * @deprecated Use useCreateChildCategoryMutation instead
 */
export const useCreateSubcategoryMutation = () => {
  const mutation = useCreateChildCategoryMutation();
  return {
    ...mutation,
    mutateAsync: async ({ categoryId, payload }: { categoryId: string; payload: { name: string } }) => {
      return mutation.mutateAsync({ parentId: categoryId, name: payload.name });
    },
  };
};
