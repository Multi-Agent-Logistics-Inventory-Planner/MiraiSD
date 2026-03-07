"use client";

import { useQuery } from "@tanstack/react-query";
import { getNotAssignedInventory } from "@/lib/api/inventory";
import type { NotAssignedInventory } from "@/types/api";

/** Exclude child/prize products - storage shows only root (parent) products. */
function filterToRootProducts(items: NotAssignedInventory[]): NotAssignedInventory[] {
  return items.filter((x) => !x.item.parentId);
}

export function useNotAssignedInventory() {
  return useQuery<NotAssignedInventory[]>({
    queryKey: ["notAssignedInventory"],
    queryFn: async () => {
      const data = await getNotAssignedInventory();
      return filterToRootProducts(data);
    },
    staleTime: 30_000, // 30 seconds
  });
}
