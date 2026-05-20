"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { LootboxPlay } from "@/types/lootbox";

interface MyPrizesListProps {
  prizes: LootboxPlay[] | undefined;
  isLoading: boolean;
}

function formatDate(value: string) {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

export function MyPrizesList({ prizes, isLoading }: MyPrizesListProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">My prizes</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : !prizes || prizes.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            You haven&apos;t won anything yet. Open a lootbox!
          </p>
        ) : (
          <ul className="divide-y">
            {prizes.map((p) => (
              <li key={p.id} className="py-3 flex items-center justify-between gap-3">
                <div className="min-w-0 space-y-1">
                  <div className="font-medium truncate">{p.prizeName}</div>
                  <div className="text-xs text-muted-foreground">
                    {p.prizeTierName} • won {formatDate(p.playedAt)}
                    {p.status === "REDEEMED" && p.redeemedAt
                      ? ` • redeemed ${formatDate(p.redeemedAt)}`
                      : ""}
                  </div>
                </div>
                <Badge variant={p.status === "REDEEMED" ? "secondary" : "default"}>
                  {p.status === "REDEEMED" ? "Redeemed" : "Pending"}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
