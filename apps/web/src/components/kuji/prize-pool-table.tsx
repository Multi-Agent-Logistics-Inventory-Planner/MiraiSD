"use client";

import { useMemo, useState } from "react";
import type { KujiBoxTier } from "@/types/api";
import { cn } from "@/lib/utils";
import {
  formatMoney,
  formatMoneyDecimals,
} from "@/lib/utils/format-money";
import { ProductImageLightbox } from "@/components/products/product-image-lightbox";
import { getSafeImageUrl } from "@/lib/utils/validation";
import { compareTiers, hexWithAlpha, tierColor } from "./tier-palette";
import { TierClassColorProvider, useTierClassColor } from "./tier-class-color-context";
import { TierName } from "./tier-name";
import { TierThumb, type TierThumbExpand } from "./tier-thumb";
import { effectivePrice } from "./kuji-value-rollups";

interface PrizePoolTableProps {
  readonly tiers: readonly KujiBoxTier[];
}

interface Row {
  tier: KujiBoxTier;
  rank: number;
  price: number | null;
  active: number;
  held: number;
  drawn: number;
  remaining: number;
  valueDrawn: number;
  fullyDrawn: boolean;
  heldOnly: boolean;
}

export function PrizePoolTable({ tiers }: PrizePoolTableProps) {
  const [lightbox, setLightbox] = useState<TierThumbExpand | null>(null);

  const rows: Row[] = useMemo(() => {
    const sorted = [...tiers].sort(compareTiers);
    const list = sorted.map((t, idx) => {
      const price = effectivePrice(t);
      const active = t.activeCount ?? 0;
      const held = t.inactiveCount ?? 0;
      const drawn = t.drawnCount ?? 0;
      const inBox = active + held;
      const remaining = price != null ? inBox * price : 0;
      const valueDrawn = price != null ? drawn * price : 0;
      return {
        tier: t,
        rank: idx,
        price,
        active,
        held,
        drawn,
        remaining,
        valueDrawn,
        fullyDrawn: active === 0 && held === 0 && drawn > 0,
        heldOnly: active === 0 && held > 0,
      };
    });
    list.sort((a, b) => {
      if (a.fullyDrawn !== b.fullyDrawn) return a.fullyDrawn ? 1 : -1;
      return b.remaining - a.remaining;
    });
    return list;
  }, [tiers]);

  const totalRemaining = rows.reduce((s, r) => s + r.remaining, 0);
  const maxRemaining = rows.reduce((m, r) => Math.max(m, r.remaining), 0);
  const prizesRemaining = rows.reduce((s, r) => s + r.active + r.held, 0);

  return (
    <TierClassColorProvider tiers={tiers}>
    <div className="rounded-xl border bg-card p-4 dark:border-none">
      <div className="mb-3 flex items-center justify-between border-b pb-2.5">
        <span className="text-sm font-medium">Prize pool</span>
        <span className="text-[11px] text-muted-foreground tabular-nums">
          {prizesRemaining} prize{prizesRemaining === 1 ? "" : "s"} ·{" "}
          {formatMoney(totalRemaining)} remaining
        </span>
      </div>
      {rows.length === 0 ? (
        <div className="py-6 text-center text-xs text-muted-foreground">
          No tiers in this box.
        </div>
      ) : (
        <>
          {/* Mobile: stacked card rows with colored left stripe */}
          <div className="max-h-96 overflow-y-auto scrollbar-none md:hidden">
            <ul className="divide-y">
              {rows.map((r) => (
                <PrizeRowMobile
                  key={r.tier.id}
                  row={r}
                  onExpand={setLightbox}
                />
              ))}
            </ul>
          </div>

          {/* Desktop: full columned table */}
          <div className="hidden md:block">
            <div className="grid grid-cols-[32px_minmax(0,1fr)_80px_84px_140px_110px] items-center gap-3 border-b pb-2 text-[10.5px] uppercase tracking-wider text-muted-foreground">
              <span aria-hidden />
              <span>Prize</span>
              <span className="text-right">Unit</span>
              <span className="text-right">In box</span>
              <span className="text-right">Value remaining</span>
              <span className="text-right">Value paid out</span>
            </div>
            <div className="max-h-[28rem] overflow-y-auto scrollbar-none">
              <ul className="divide-y">
                {rows.map((r) => (
                  <PrizeRow
                    key={r.tier.id}
                    row={r}
                    maxRemaining={maxRemaining}
                    onExpand={setLightbox}
                  />
                ))}
              </ul>
            </div>
          </div>
        </>
      )}
      <ProductImageLightbox
        open={lightbox !== null}
        onOpenChange={(open) => {
          if (!open) setLightbox(null);
        }}
        imageUrl={lightbox?.url}
        alt={lightbox?.alt ?? ""}
      />
    </div>
    </TierClassColorProvider>
  );
}

interface PrizeRowProps {
  readonly row: Row;
  readonly maxRemaining: number;
  readonly onExpand: (state: TierThumbExpand) => void;
}

function PrizeRow({ row, maxRemaining, onExpand }: PrizeRowProps) {
  const { tier, rank, price, active, held, drawn, remaining, valueDrawn } = row;
  const color = tierColor(rank);
  const classColor = useTierClassColor(tier.label);
  const hasImage = !!getSafeImageUrl(tier.linkedProductImageUrl);
  const barPct =
    maxRemaining > 0 ? Math.max(0.05, remaining / maxRemaining) : 0;
  const barWidth = remaining > 0 ? `${Math.round(barPct * 100)}%` : "0%";

  const dim = row.fullyDrawn ? "opacity-60" : "";

  return (
    <li
      className={cn(
        "grid grid-cols-[32px_minmax(0,1fr)_80px_84px_140px_110px] items-center gap-3 min-h-[44px] py-2 text-xs",
        dim
      )}
    >
      {hasImage ? (
        <TierThumb
          tier={tier}
          rank={rank}
          size={28}
          dashed={row.heldOnly || row.fullyDrawn}
          onExpand={onExpand}
        />
      ) : (
        <span
          className="mx-auto h-1.5 w-1.5 rounded-full"
          style={{ background: classColor }}
          aria-hidden
        />
      )}
      <div className="min-w-0 flex-1">
        <TierName tier={tier} colorSecondary />
        {price == null && (
          <span className="ml-2 inline-block rounded-md bg-amber-500/15 px-1.5 py-0.5 text-[10px] text-amber-500">
            set price to include in totals
          </span>
        )}
      </div>
      <span className="text-right tabular-nums text-muted-foreground">
        {price == null ? "—" : formatMoneyDecimals(price)}
      </span>
      <span className="text-right tabular-nums">
        {row.fullyDrawn ? (
          "—"
        ) : (
          <>
            {active}
            {held > 0 && (
              <span className="ml-1 text-amber-500">+{held}h</span>
            )}
          </>
        )}
      </span>
      <div className="flex items-center justify-end gap-2">
        {!row.fullyDrawn && remaining > 0 && (
          <div
            className="h-1.5 rounded-full"
            style={{
              width: barWidth,
              maxWidth: 100,
              background: hexWithAlpha(color, 0.85),
            }}
            aria-hidden
          />
        )}
        <span className="tabular-nums">
          {row.fullyDrawn || remaining === 0 ? "—" : formatMoney(remaining)}
        </span>
      </div>
      <div className="flex flex-col items-end leading-tight tabular-nums text-muted-foreground">
        {drawn === 0 ? (
          <span>—</span>
        ) : (
          <>
            <span>{price == null ? "—" : formatMoney(valueDrawn)}</span>
            <span className="text-[10px] opacity-70">×{drawn}</span>
          </>
        )}
      </div>
    </li>
  );
}

interface PrizeRowMobileProps {
  readonly row: Row;
  readonly onExpand: (state: TierThumbExpand) => void;
}

function PrizeRowMobile({ row, onExpand }: PrizeRowMobileProps) {
  const { tier, rank, price, active, held, drawn, remaining, valueDrawn } = row;
  const color = useTierClassColor(tier.label);
  const hasImage = !!getSafeImageUrl(tier.linkedProductImageUrl);
  const totalUnits = active + held + drawn;
  const wonPct = totalUnits > 0 ? Math.round((drawn / totalUnits) * 100) : 0;
  const dim = row.fullyDrawn ? "opacity-60" : "";

  return (
    <li className={cn("flex items-stretch gap-3 min-h-[52px] py-2.5 text-xs", dim)}>
      <div
        className="w-0.5 shrink-0 self-stretch rounded-full"
        style={{ background: color }}
        aria-hidden
      />
      {hasImage ? (
        <TierThumb
          tier={tier}
          rank={rank}
          size={36}
          dashed={row.heldOnly || row.fullyDrawn}
          onExpand={onExpand}
        />
      ) : (
        <span
          className="mt-1.5 h-2 w-2 shrink-0 rounded-full"
          style={{ background: color }}
          aria-hidden
        />
      )}
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <TierName
              tier={tier}
              className="block truncate text-sm"
              colorSecondary
            />
            <div className="mt-0.5 flex items-center gap-1.5 text-[11px] text-muted-foreground tabular-nums">
              {tier.letter ? <span>{tier.letter}</span> : null}
              <span>·</span>
              <span>
                {price == null ? "no price" : formatMoneyDecimals(price)}
              </span>
              {price == null && (
                <span className="rounded-md bg-amber-500/15 px-1.5 py-0.5 text-[10px] text-amber-500">
                  set price
                </span>
              )}
            </div>
          </div>
          <div className="text-right tabular-nums">
            <div className="text-base font-medium leading-none">
              {row.fullyDrawn ? (
                "—"
              ) : (
                <>
                  {active}
                  {held > 0 && (
                    <span className="ml-1 text-[11px] font-normal text-amber-500">
                      +{held}h
                    </span>
                  )}
                </>
              )}
            </div>
            <div className="mt-1 text-[11px] text-muted-foreground">
              {price == null
                ? `— · ${wonPct}% won`
                : row.fullyDrawn
                  ? valueDrawn > 0
                    ? `${formatMoney(valueDrawn)} paid out`
                    : "—"
                  : `${formatMoney(remaining)} · ${wonPct}% won`}
            </div>
          </div>
        </div>
        {totalUnits > 0 && (
          <div className="mt-2 h-0.5 w-full overflow-hidden rounded-full bg-muted/40">
            <div
              className="h-full rounded-full"
              style={{ width: `${wonPct}%`, background: color }}
              aria-hidden
            />
          </div>
        )}
      </div>
    </li>
  );
}
