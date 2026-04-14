"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  recomputeRollups,
  type RecomputeRollupsResponse,
} from "@/lib/api/analytics";
import { queryKeys } from "@/lib/query-keys";

export function useRecomputeRollupsMutation() {
  const queryClient = useQueryClient();

  return useMutation<RecomputeRollupsResponse, Error>({
    mutationFn: recomputeRollups,
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.analytics.all(),
      });
    },
  });
}
