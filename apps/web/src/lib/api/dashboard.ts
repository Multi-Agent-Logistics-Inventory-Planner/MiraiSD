import type { AuditLogEntry, InventoryItem } from "@/types/api";
import { getInventoryTotals } from "@/lib/api/inventory";
import { getAuditLog } from "@/lib/api/stock-movements";

export interface InventoryTotals {
  byItemId: Record<
    string,
    {
      item: InventoryItem;
      quantity: number;
      lastUpdatedAt: string;
    }
  >;
}

/**
 * Fetch aggregated inventory totals for all items.
 * Uses a single optimized backend query instead of N+1 calls.
 */
export async function getInventoryTotalsByItemId(): Promise<InventoryTotals> {
  const totals = await getInventoryTotals();

  const byItemId: InventoryTotals["byItemId"] = {};

  for (const total of totals) {
    byItemId[total.itemId] = {
      item: {
        id: total.itemId,
        sku: total.sku,
        name: total.name,
        imageUrl: total.imageUrl ?? undefined,
        category: {
          id: total.categoryId ?? "",
          parentId: total.parentCategoryId ?? null,
          name: total.categoryName ?? "",
          slug: "",
          displayOrder: 0,
          isActive: true,
          children: [],
          createdAt: "",
          updatedAt: "",
        },
      },
      quantity: total.totalQuantity,
      lastUpdatedAt: total.lastUpdatedAt ?? new Date().toISOString(),
    };
  }

  return { byItemId };
}

/**
 * Fetch all audit log entries for the past 30 days.
 * Uses a large page size (500) which is sufficient for <100 changes/day.
 */
export async function getAuditLogLast30Days(): Promise<AuditLogEntry[]> {
  const now = new Date();
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
  const fromDate = thirtyDaysAgo.toISOString().split("T")[0];
  const toDate = now.toISOString().split("T")[0];

  const response = await getAuditLog({ fromDate, toDate }, 0, 500);
  return response.content;
}
