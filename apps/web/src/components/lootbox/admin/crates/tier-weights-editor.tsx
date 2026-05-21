"use client";

import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import type { LootboxTier } from "@/types/lootbox";

interface TierWeightsEditorProps {
  readonly tiers: readonly LootboxTier[];
  /** Externally-controlled override map: tier.id → percentage string. */
  readonly edits: Readonly<Record<string, string>>;
  readonly onEditsChange: (edits: Record<string, string>) => void;
}

export function tierWeightSum(
  tiers: readonly LootboxTier[],
  edits: Readonly<Record<string, string>>
): number {
  return tiers.reduce((acc, t) => {
    const raw = edits[t.id] ?? Number(t.probabilityPct).toFixed(2);
    return acc + (Number.parseFloat(raw) || 0);
  }, 0);
}

/**
 * Editable list of tier weight inputs with a running sum readout. The parent
 * owns the edits map and reads back the sum via `tierWeightSum` — no upward
 * effect-driven state sync.
 */
export function TierWeightsEditor({
  tiers,
  edits,
  onEditsChange,
}: TierWeightsEditorProps) {
  const valueFor = (t: LootboxTier) =>
    edits[t.id] ?? Number(t.probabilityPct).toFixed(2);

  const sum = tierWeightSum(tiers, edits);
  const sumOk = Math.abs(sum - 100) < 0.05;

  const setEdit = (id: string, value: string) => {
    onEditsChange({ ...edits, [id]: value });
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Tier weights
        </span>
        <span
          className={cn(
            "font-mono text-[11px] tabular-nums",
            sumOk ? "text-emerald-500" : "text-rose-500"
          )}
        >
          Total {sum.toFixed(2)}%
        </span>
      </div>
      <ul className="space-y-2">
        {tiers.map((t) => (
          <li
            key={t.id}
            className="grid grid-cols-[120px_1fr_80px] items-center gap-3.5"
          >
            <span
              className="inline-flex justify-center rounded-full px-3 py-1 font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em]"
              style={{
                backgroundColor: t.displayColor ?? "#8a8a93",
                color: pickContrastText(t.displayColor),
              }}
            >
              {t.name}
            </span>
            <div className="relative">
              <Input
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={valueFor(t)}
                onChange={(e) => setEdit(t.id, e.target.value)}
                className="pr-7 font-mono tabular-nums"
              />
              <span className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 font-mono text-[11px] text-muted-foreground">
                %
              </span>
            </div>
            <span className="text-right font-mono text-[11px] text-muted-foreground">
              {t.prizes.length} prize{t.prizes.length === 1 ? "" : "s"}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function pickContrastText(bg: string | null): string {
  // Light tier colors (Common gray, Legendary gold) read better with dark text.
  if (!bg) return "#0a0a0c";
  const hex = bg.replace("#", "");
  if (hex.length !== 6) return "#fff";
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.6 ? "#0a0a0c" : "#fff";
}
