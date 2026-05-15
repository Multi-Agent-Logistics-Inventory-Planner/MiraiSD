"use client";

import * as React from "react";
import type { KujiBoxTier } from "@/types/api";
import {
  buildTierClassColorMap,
  normalizeLabel,
  tierClassColor,
} from "./kuji-tier-class";

const TierClassColorContext =
  React.createContext<ReadonlyMap<string, string> | null>(null);

interface TierClassColorProviderProps {
  readonly tiers: readonly KujiBoxTier[];
  readonly children: React.ReactNode;
}

export function TierClassColorProvider({
  tiers,
  children,
}: TierClassColorProviderProps) {
  const map = React.useMemo(() => buildTierClassColorMap(tiers), [tiers]);
  return (
    <TierClassColorContext.Provider value={map}>
      {children}
    </TierClassColorContext.Provider>
  );
}

export function useTierClassColor(label: string | null | undefined): string {
  const map = React.useContext(TierClassColorContext);
  const key = normalizeLabel(label);
  if (map && key) {
    const resolved = map.get(key);
    if (resolved) return resolved;
  }
  return tierClassColor(label);
}
