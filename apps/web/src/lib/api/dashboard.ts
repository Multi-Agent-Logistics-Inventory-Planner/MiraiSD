import type { AuditLogEntry } from "@/types/api";
import { getAuditLog } from "@/lib/api/stock-movements";

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
