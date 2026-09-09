"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { getCatalogCategories } from "@/lib/api/categories";

export function useCategories() {
  return useQuery({
    queryKey: ["categories"],
    // Categories are wholly global (no siteId) - spec.md phase-5d AC-7. Unlike products, this
    // is a straight endpoint swap, not a join: every consumer of useCategories/useChildCategories
    // moves to v1 at once, with no query-key change needed.
    queryFn: getCatalogCategories,
    staleTime: 5 * 60 * 1000, // Categories change infrequently
    select: (data) =>
      [...data]
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((c) => ({
          ...c,
          children: c.children
            ? [...c.children].sort((a, b) => a.name.localeCompare(b.name))
            : c.children,
        })),
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
