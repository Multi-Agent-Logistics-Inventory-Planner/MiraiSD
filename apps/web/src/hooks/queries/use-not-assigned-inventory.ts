"use client";

import { useQuery } from "@tanstack/react-query";
import { getNotAssignedInventory } from "@/lib/api/inventory";
import type { NotAssignedInventory } from "@/types/api";

export function useNotAssignedInventory() {
  return useQuery<NotAssignedInventory[]>({
    queryKey: ["notAssignedInventory"],
    queryFn: getNotAssignedInventory,
  });
}
