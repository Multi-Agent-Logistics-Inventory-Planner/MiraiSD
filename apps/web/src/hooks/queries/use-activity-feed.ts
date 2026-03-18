"use client";

import { useCallback, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getActivityFeed } from "@/lib/api/activity-feed";
import type { ActivityEventType } from "@/types/dashboard";

const INITIAL_LIMIT = 50;
const LOAD_MORE_INCREMENT = 20;

export interface ActivityFeedFilters {
  types: ActivityEventType[];
  showResolved: boolean;
}

const DEFAULT_FILTERS: ActivityFeedFilters = {
  types: ["alert", "restock", "sale", "shipment", "adjustment", "transfer"],
  showResolved: false,
};

export function useActivityFeed(filters: ActivityFeedFilters = DEFAULT_FILTERS) {
  const [limit, setLimit] = useState(INITIAL_LIMIT);

  // Fetch activity feed from unified endpoint (single request instead of 3)
  const query = useQuery({
    queryKey: ["activity-feed", filters.types, filters.showResolved, limit],
    queryFn: () => getActivityFeed({
      limit,
      types: filters.types,
      includeResolved: filters.showResolved,
    }),
    staleTime: 30 * 1000,
  });

  const events = query.data ?? [];
  const hasMore = events.length >= limit;

  const loadMore = useCallback(() => {
    setLimit((prev) => prev + LOAD_MORE_INCREMENT);
  }, []);

  const resetLimit = useCallback(() => {
    setLimit(INITIAL_LIMIT);
  }, []);

  return {
    events,
    totalCount: events.length,
    isLoading: query.isLoading,
    error: query.error as Error | null,
    hasMore,
    loadMore,
    resetLimit,
  };
}
