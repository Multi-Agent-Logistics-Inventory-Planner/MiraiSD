"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getTracking,
  type TrackingLookupResponse,
} from "@/lib/api/tracking";

export function useTracking(trackingNumber: string | null) {
  return useQuery<TrackingLookupResponse, Error>({
    queryKey: ["tracking", trackingNumber],
    queryFn: () => getTracking(trackingNumber as string),
    enabled: !!trackingNumber,
    staleTime: 15 * 60 * 1000, // 15 minutes
    gcTime: 30 * 60 * 1000, // keep in cache for 30 minutes
    retry: false, // Don't retry on failure to avoid duplicate requests
    refetchOnWindowFocus: false, // Don't refetch when window regains focus
  });
}

