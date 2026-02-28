"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { getCategories } from "@/lib/api/categories";

export function useCategories() {
  return useQuery({
    queryKey: ["categories"],
    queryFn: getCategories,
    staleTime: 5 * 60 * 1000, // Categories change infrequently
  });
}

/**
 * Get child categories (subcategories) for a specific parent category.
 * In the new single-table design, subcategories are just categories with a parentId.
 */
export function useChildCategories(parentCategoryId: string | undefined) {
  const { data: categories } = useCategories();

  return useMemo(() => {
    if (!parentCategoryId || !categories) return [];
    const category = categories.find((c) => c.id === parentCategoryId);
    return category?.children ?? [];
  }, [parentCategoryId, categories]);
}

/**
 * @deprecated Use useChildCategories instead
 */
export const useSubcategories = useChildCategories;
