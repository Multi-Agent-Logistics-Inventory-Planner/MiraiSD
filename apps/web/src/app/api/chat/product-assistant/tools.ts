import { tool } from "ai";
import { z } from "zod";

import { BACKEND_BASE_URL } from "@/lib/api/backend-url";
import type { StockMovementReason } from "@/lib/api/product-assistant";

/**
 * Shape of the closure context bound at the streamText call site via
 * {@code experimental_context}. The productId is NEVER part of any tool's
 * inputSchema - the LLM has no mechanism to address a different product
 * mid-session. The JWT is passed through to authenticate backend calls.
 */
export interface AssistantContext {
  productId: string;
  jwt: string;
}

async function backendGet<T>(path: string, jwt: string): Promise<T> {
  const res = await fetch(`${BACKEND_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${jwt}` },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Data fetch failed (${res.status}). Please try again.`);
  }
  return (await res.json()) as T;
}

function assertContext(ctx: unknown): AssistantContext {
  const typed = ctx as Partial<AssistantContext> | undefined;
  if (!typed?.productId || !typed?.jwt) {
    throw new Error("Missing assistant context");
  }
  return typed as AssistantContext;
}

const REASON_VALUES = [
  "INITIAL_STOCK",
  "RESTOCK",
  "SHIPMENT_RECEIPT",
  "SHIPMENT_RECEIPT_REVERSED",
  "SALE",
  "DAMAGE",
  "ADJUSTMENT",
  "RETURN",
  "TRANSFER",
  "REMOVED",
  "DISPLAY_SET",
  "DISPLAY_REMOVED",
  "DISPLAY_SWAP",
] as const satisfies ReadonlyArray<StockMovementReason>;

export const productAssistantTools = {
  getMovementSummary: tool({
    description:
      "Aggregate stock movement summary (counts by reason, last-of-reason timestamps, biggest single day) for the current product over a date range. Default answer for most movement-history questions.",
    inputSchema: z.object({
      fromDate: z
        .string()
        .describe("ISO-8601 start timestamp, inclusive (e.g. 2026-03-10T00:00:00Z)"),
      toDate: z
        .string()
        .describe("ISO-8601 end timestamp, exclusive (e.g. 2026-04-09T00:00:00Z)"),
    }),
    execute: async (input, options) => {
      const { productId, jwt } = assertContext(
        (options as { experimental_context?: unknown }).experimental_context,
      );
      const qs = new URLSearchParams({ from: input.fromDate, to: input.toDate });
      return backendGet(
        `/api/analytics/products/${productId}/movements/summary?${qs}`,
        jwt,
      );
    },
  }),

  getProductMovements: tool({
    description:
      "Raw stock movement rows for the current product within a date range. Use only when the user asks for specific row-level detail that the summary cannot answer. Limit capped at 200.",
    inputSchema: z.object({
      fromDate: z.string().describe("ISO-8601 start timestamp, inclusive"),
      toDate: z.string().describe("ISO-8601 end timestamp, exclusive"),
      reasons: z
        .array(z.enum(REASON_VALUES))
        .optional()
        .describe("Optional filter; omit for all reasons"),
      limit: z.number().int().min(1).max(200).default(50),
    }),
    execute: async (input, options) => {
      const { productId, jwt } = assertContext(
        (options as { experimental_context?: unknown }).experimental_context,
      );
      const qs = new URLSearchParams({
        from: input.fromDate,
        to: input.toDate,
        limit: String(input.limit),
      });
      if (input.reasons?.length) qs.set("reasons", input.reasons.join(","));
      return backendGet(
        `/api/analytics/products/${productId}/movements?${qs}`,
        jwt,
      );
    },
  }),

  getCategoryPeers: tool({
    description:
      "Top-N peers in the same category ranked by a metric. Use for comparative questions.",
    inputSchema: z.object({
      metric: z
        .enum(["sales_velocity", "days_to_stockout"])
        .describe("Which metric to rank by"),
      limit: z.number().int().min(1).max(20).default(5),
    }),
    execute: async (input, options) => {
      const { productId, jwt } = assertContext(
        (options as { experimental_context?: unknown }).experimental_context,
      );
      const qs = new URLSearchParams({
        metric: input.metric,
        limit: String(input.limit),
      });
      return backendGet(
        `/api/analytics/products/${productId}/comparison?${qs}`,
        jwt,
      );
    },
  }),
};
