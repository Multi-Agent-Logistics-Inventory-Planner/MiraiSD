"use client";

import { useState, useCallback, useMemo } from "react";

const STORAGE_KEY = "mirai-dismissed-predictions";
const ARCHIVE_THRESHOLD_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

interface DismissedEntry {
  dismissedAt: number;
  computedAt: string | null;
}

type DismissedMap = Record<string, DismissedEntry>;

function readStorage(): DismissedMap {
  if (typeof window === "undefined") return {};
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return {};

    // Purge entries older than 30 days
    const now = Date.now();
    const entries = Object.entries(parsed as DismissedMap);
    const fresh = entries.filter(
      ([, entry]) => typeof entry.dismissedAt === "number" && now - entry.dismissedAt < ARCHIVE_THRESHOLD_MS,
    );

    if (fresh.length !== entries.length) {
      const cleaned = Object.fromEntries(fresh);
      writeStorage(cleaned);
      return cleaned;
    }

    return parsed as DismissedMap;
  } catch {
    return {};
  }
}

function writeStorage(map: DismissedMap): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    // localStorage full or unavailable -- silently ignore
  }
}

export function useDismissedPredictions() {
  const [dismissedMap, setDismissedMap] = useState<DismissedMap>(readStorage);

  const dismissedIds = useMemo(
    () => new Set(Object.keys(dismissedMap)),
    [dismissedMap],
  );

  const dismiss = useCallback((itemId: string, computedAt: string | null) => {
    setDismissedMap((prev) => {
      const next = { ...prev, [itemId]: { dismissedAt: Date.now(), computedAt } };
      writeStorage(next);
      return next;
    });
  }, []);

  const restore = useCallback((itemId: string) => {
    setDismissedMap((prev) => {
      const { [itemId]: _, ...next } = prev;
      writeStorage(next);
      return next;
    });
  }, []);

  return { dismissedMap, dismissedIds, dismiss, restore } as const;
}
