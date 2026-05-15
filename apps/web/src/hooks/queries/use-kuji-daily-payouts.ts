"use client";

import { useQuery } from "@tanstack/react-query";
import { getKujiDailyPayouts } from "@/lib/api/kuji-boxes";

export function useKujiDailyPayouts(
  boxId: string | null | undefined,
  params: { from?: string; to?: string; tz: string }
) {
  return useQuery({
    queryKey: [
      "kuji-box",
      "daily-payouts",
      boxId,
      params.from ?? null,
      params.to ?? null,
      params.tz,
    ],
    queryFn: () => getKujiDailyPayouts(boxId!, params),
    enabled: !!boxId,
  });
}
