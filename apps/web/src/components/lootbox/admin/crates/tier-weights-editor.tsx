"use client";

import { Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import type { LootboxTier } from "@/types/lootbox";

interface TierWeightsEditorProps {
  readonly tiers: readonly LootboxTier[];
  /** Externally-controlled override map: tier.id → percentage string. */
  readonly edits: Readonly<Record<string, string>>;
  readonly onEditsChange: (edits: Record<string, string>) => void;
  /** Renders a delete button per row when provided. Parent owns the confirm + mutation. */
  readonly onDeleteTier?: (tier: LootboxTier) => void;
  /** Disables the delete button for tiers whose id appears here (e.g. has active prizes, mutation pending). */
  readonly deleteDisabledFor?: ReadonlySet<string>;
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
  onDeleteTier,
  deleteDisabledFor,
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
        {tiers.map((t) => {
          const deleteDisabled = deleteDisabledFor?.has(t.id) ?? false;
          // Backend rejects probability > 0 on a tier with no active prizes, so
          // disable the input rather than letting the admin type a value that
          // will fail validation on save. (See LootboxAdminService.bulkUpdate...)
          const activePrizeCount = t.prizes.filter((p) => p.active).length;
          const noPrizesYet = activePrizeCount === 0;
          return (
            <li
              key={t.id}
              className={cn(
                "grid items-center gap-3.5",
                onDeleteTier
                  ? "grid-cols-[120px_1fr_70px_30px]"
                  : "grid-cols-[120px_1fr_80px]",
                !t.active && "opacity-55"
              )}
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
              {noPrizesYet ? (
                <div
                  className="flex h-9 items-center rounded-md border border-dashed border-border px-3 font-mono text-[11px] uppercase tracking-[0.1em] text-muted-foreground"
                  title="Add an active prize before giving this tier weight."
                >
                  Add prize first
                </div>
              ) : (
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
              )}
              <div className="flex flex-col items-end gap-0.5 text-right font-mono text-[11px] text-muted-foreground">
                <span>
                  {t.prizes.length} prize{t.prizes.length === 1 ? "" : "s"}
                </span>
                {!t.active ? (
                  <span className="rounded-full bg-card px-1.5 py-0.5 text-[9.5px] uppercase tracking-[0.1em]">
                    Inactive
                  </span>
                ) : null}
              </div>
              {onDeleteTier ? (
                <button
                  type="button"
                  onClick={() => onDeleteTier(t)}
                  disabled={deleteDisabled}
                  aria-label={`Delete tier ${t.name}`}
                  title={`Delete tier ${t.name}`}
                  className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-md border border-border text-muted-foreground hover:bg-card/80 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              ) : null}
            </li>
          );
        })}
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
