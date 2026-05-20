"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { LootboxTier } from "@/types/lootbox";

interface DropRatesPanelProps {
  tiers: LootboxTier[] | undefined;
  isLoading: boolean;
}

export function DropRatesPanel({ tiers, isLoading }: DropRatesPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Drop rates</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <>
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-full" />
          </>
        ) : !tiers || tiers.length === 0 ? (
          <p className="text-sm text-muted-foreground">No prizes available yet.</p>
        ) : (
          tiers.map((tier) => (
            <div key={tier.id} className="space-y-2">
              <div className="flex items-center justify-between">
                <Badge
                  variant="secondary"
                  style={
                    tier.displayColor
                      ? { backgroundColor: tier.displayColor, color: "white" }
                      : undefined
                  }
                >
                  {tier.name}
                </Badge>
                <span className="text-sm font-medium">
                  {Number(tier.probabilityPct).toFixed(2)}%
                </span>
              </div>
              {tier.prizes.length === 0 ? (
                <p className="pl-2 text-xs text-muted-foreground">No prizes in this tier.</p>
              ) : (
                <ul className="pl-2 space-y-1 text-sm">
                  {tier.prizes.map((p) => (
                    <li key={p.id} className="text-muted-foreground">
                      • {p.name}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}
