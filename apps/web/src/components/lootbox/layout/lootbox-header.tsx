"use client";

import { Coins, Sliders } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { useLootboxWalletBreakdown } from "@/hooks/queries/use-lootbox";

interface LootboxHeaderProps {
  readonly balance: number;
  readonly openedToday: number;
  readonly isAdmin: boolean;
  readonly onOpenAdmin: () => void;
}

export function LootboxHeader({
  balance,
  openedToday,
  isAdmin,
  onOpenAdmin,
}: LootboxHeaderProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap items-center gap-3.5">
        <CoinBalanceBadge balance={balance} />
        <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
          <span className="tabular-nums">{openedToday}</span> opened today
        </span>
      </div>

      {isAdmin ? (
        <Button variant="outline" size="sm" onClick={onOpenAdmin}>
          <Sliders className="h-4 w-4" />
          Admin
        </Button>
      ) : null}
    </div>
  );
}

function CoinBalanceBadge({ balance }: { readonly balance: number }) {
  const breakdownQuery = useLootboxWalletBreakdown();
  const breakdown = breakdownQuery.data;

  const expiringSoonTotal = (breakdown?.expiringSoon ?? []).reduce(
    (sum, b) => sum + b.amount,
    0
  );
  const nextExpiry = breakdown?.nextExpiryDate ?? null;
  const daysUntilNext = nextExpiry ? daysUntil(nextExpiry) : null;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="inline-flex flex-col items-start gap-0.5 rounded-full border border-border bg-card px-3 py-1.5 text-left hover:bg-card/80"
          aria-label="Open wallet breakdown"
        >
          <span className="inline-flex items-center gap-2">
            <Coins className="h-3.5 w-3.5 text-amber-500" />
            <span className="font-mono text-[12px] tabular-nums text-foreground">
              {balance}
            </span>
            <span className="font-mono text-[11px] text-muted-foreground">
              coin{balance === 1 ? "" : "s"}
            </span>
          </span>
          {expiringSoonTotal > 0 && daysUntilNext !== null ? (
            <span className="font-mono text-[10px] uppercase tracking-[0.12em] text-rose-500/80">
              {expiringSoonTotal} expiring in {daysUntilNext}d
            </span>
          ) : null}
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-72 p-3">
        <div className="space-y-2">
          <div className="text-sm font-medium">Wallet</div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">Spendable</span>
            <span className="font-mono tabular-nums">{balance}</span>
          </div>
          {breakdownQuery.isLoading ? (
            <p className="text-xs text-muted-foreground">Loading expiry…</p>
          ) : (breakdown?.expiringSoon ?? []).length === 0 ? (
            <p className="text-xs text-muted-foreground">
              No coins expiring in the next 30 days.
            </p>
          ) : (
            <>
              <div className="border-t pt-2 text-xs uppercase tracking-[0.12em] text-muted-foreground">
                Expiring soon
              </div>
              <ul className="space-y-1 text-sm">
                {breakdown!.expiringSoon.map((b) => (
                  <li
                    key={b.expiresOn}
                    className="flex items-center justify-between"
                  >
                    <span>{formatExpiryDate(b.expiresOn)}</span>
                    <span className="font-mono tabular-nums text-rose-600">
                      −{b.amount}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}

function daysUntil(iso: string): number {
  const day = new Date(iso);
  if (Number.isNaN(day.getTime())) return 0;
  const diff = day.getTime() - Date.now();
  return Math.max(0, Math.ceil(diff / (24 * 60 * 60 * 1000)));
}

function formatExpiryDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}
