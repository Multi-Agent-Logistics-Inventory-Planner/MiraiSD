"use client";

import { useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dismissForecast,
  listForecastDismissals,
  restoreForecastDismissal,
  type PredictionDismissalDTO,
} from "@/lib/api/forecasts";

const DISMISSALS_QUERY_KEY = ["forecast-dismissals"] as const;
const STALE_TIME_MS = 60_000;

interface DismissedEntry {
  dismissedAt: number;
  computedAt: string | null;
}

type DismissedMap = Record<string, DismissedEntry>;

function toMap(rows: PredictionDismissalDTO[]): DismissedMap {
  const map: DismissedMap = {};
  for (const row of rows) {
    map[row.itemId] = {
      dismissedAt: Date.parse(row.dismissedAt),
      computedAt: row.computedAt,
    };
  }
  return map;
}

/**
 * Org-wide dismissed-predictions state. Persists across browsers/sessions via
 * the inventory-service /api/forecasts/dismissals endpoints. Dismissals
 * auto-expire on the server after 30 days; this hook only renders what the
 * server currently treats as active.
 *
 * Mutations are optimistic so the UI feels instant -- a failed POST/DELETE
 * rolls back to the previous server state on the next invalidation.
 */
export function useDismissedPredictions() {
  const queryClient = useQueryClient();

  const { data: rows = [] } = useQuery({
    queryKey: DISMISSALS_QUERY_KEY,
    queryFn: listForecastDismissals,
    staleTime: STALE_TIME_MS,
  });

  const dismissedMap = useMemo(() => toMap(rows), [rows]);
  const dismissedIds = useMemo(
    () => new Set(Object.keys(dismissedMap)),
    [dismissedMap],
  );

  const dismissMutation = useMutation({
    mutationFn: ({ itemId, computedAt }: { itemId: string; computedAt: string | null }) =>
      dismissForecast(itemId, computedAt),
    onMutate: async ({ itemId, computedAt }) => {
      await queryClient.cancelQueries({ queryKey: DISMISSALS_QUERY_KEY });
      const previous = queryClient.getQueryData<PredictionDismissalDTO[]>(DISMISSALS_QUERY_KEY) ?? [];
      const without = previous.filter((row) => row.itemId !== itemId);
      const optimistic: PredictionDismissalDTO = {
        itemId,
        dismissedAt: new Date().toISOString(),
        dismissedBy: "",
        computedAt,
        reason: null,
      };
      queryClient.setQueryData<PredictionDismissalDTO[]>(DISMISSALS_QUERY_KEY, [optimistic, ...without]);
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(DISMISSALS_QUERY_KEY, context.previous);
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: DISMISSALS_QUERY_KEY });
    },
  });

  const restoreMutation = useMutation({
    mutationFn: (itemId: string) => restoreForecastDismissal(itemId),
    onMutate: async (itemId) => {
      await queryClient.cancelQueries({ queryKey: DISMISSALS_QUERY_KEY });
      const previous = queryClient.getQueryData<PredictionDismissalDTO[]>(DISMISSALS_QUERY_KEY) ?? [];
      queryClient.setQueryData<PredictionDismissalDTO[]>(
        DISMISSALS_QUERY_KEY,
        previous.filter((row) => row.itemId !== itemId),
      );
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(DISMISSALS_QUERY_KEY, context.previous);
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: DISMISSALS_QUERY_KEY });
    },
  });

  return {
    dismissedMap,
    dismissedIds,
    dismiss: (itemId: string, computedAt: string | null) =>
      dismissMutation.mutate({ itemId, computedAt }),
    restore: (itemId: string) => restoreMutation.mutate(itemId),
  } as const;
}
