"use client";

import { Plus, MoreHorizontal, ChevronDown, ChevronUp } from "lucide-react";
import type { KujiBoxTier } from "@/types/api";
import { cn } from "@/lib/utils";
import { formatMoney } from "@/lib/utils/format-money";
import { hexWithAlpha } from "./tier-palette";
import { formatChance, tierClassColor } from "./kuji-tier-class";

interface BaseProps {
  readonly tier: KujiBoxTier;
  readonly totalActive: number;
  readonly onBodyClick?: () => void;
}

interface RecordDrawTileProps extends BaseProps {
  readonly mode: "recordDraw";
  readonly count: number;
  readonly onDecrement: () => void;
  readonly onAddOne: () => void;
  readonly onAddFive: () => void;
}

interface ManagePrizeTileProps extends BaseProps {
  readonly mode: "managePrizes";
  readonly onStash: () => void;
  readonly onPromote: () => void;
  readonly onAdd: () => void;
  readonly onMore: () => void;
  readonly disableStash?: boolean;
  readonly disablePromote?: boolean;
  readonly disableAdd?: boolean;
}

export type TierTileProps = RecordDrawTileProps | ManagePrizeTileProps;

export function TierTile(props: TierTileProps) {
  if (props.mode === "recordDraw") {
    return <RecordDrawTile {...props} />;
  }
  return <ManagePrizeTile {...props} />;
}

function RecordDrawTile({
  tier,
  count,
  onBodyClick,
  onDecrement,
  onAddOne,
  onAddFive,
}: RecordDrawTileProps) {
  const color = tierClassColor(tier.label);
  const selected = count > 0;
  const productName = tier.linkedProductName?.trim();
  const displayName = productName || tier.label;
  const isEmpty = tier.activeCount === 0 && tier.inactiveCount === 0;

  const containerStyle: React.CSSProperties = {
    background: selected
      ? `linear-gradient(180deg, ${hexWithAlpha(color, 0.18)} 0%, var(--card) 100%)`
      : undefined,
    borderColor: selected ? hexWithAlpha(color, 0.7) : undefined,
  };

  return (
    <div
      onClick={onBodyClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onBodyClick?.();
        }
      }}
      style={containerStyle}
      className={cn(
        "relative flex flex-col rounded-[10px] border border-border bg-card overflow-hidden cursor-pointer transition-[background,border-color] duration-150",
        isEmpty && "opacity-65",
      )}
    >
      <div className="h-[3px]" style={{ background: color }} aria-hidden />

      <div className="px-3 py-2.5">
        <div className="text-[13px] leading-snug text-foreground line-clamp-2 min-h-[34px]">
          {displayName}
        </div>
        <div className="mt-1.5 flex items-baseline justify-between">
          <span
            className="text-[10px] uppercase tracking-[0.06em] font-medium"
            style={{ color }}
          >
            {tier.label}
          </span>
          <span className="text-[11.5px] tabular-nums text-muted-foreground">
            {selected ? (
              <>
                <span className="text-foreground font-medium">
                  {Math.max(0, tier.activeCount - count).toLocaleString()}
                </span>{" "}
                after
              </>
            ) : (
              <>
                <span className="text-foreground font-medium">
                  {tier.activeCount.toLocaleString()}
                </span>{" "}
                left
              </>
            )}
          </span>
        </div>
      </div>

      {selected ? (
        <>
          <div
            className="absolute -top-px -right-px text-[11px] font-semibold tabular-nums px-2 py-0.5 text-white"
            style={{
              background: color,
              borderRadius: "0 10px 0 8px",
            }}
          >
            ×{count}
          </div>
          <div
            className="flex"
            style={{ borderTop: `1px solid ${hexWithAlpha(color, 0.3)}` }}
          >
            <StepBtn
              onClick={(e) => {
                e.stopPropagation();
                onDecrement();
              }}
            >
              −
            </StepBtn>
            <StepBtn
              onClick={(e) => {
                e.stopPropagation();
                onAddFive();
              }}
            >
              +5
            </StepBtn>
            <StepBtn
              emphasized
              onClick={(e) => {
                e.stopPropagation();
                onAddOne();
              }}
            >
              +1
            </StepBtn>
          </div>
        </>
      ) : null}
    </div>
  );
}

function StepBtn({
  emphasized,
  onClick,
  children,
}: {
  emphasized?: boolean;
  onClick: (e: React.MouseEvent) => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex-1 py-[7px] text-xs tabular-nums text-foreground border-l border-border first:border-l-0 cursor-pointer transition-colors hover:bg-white/[0.04]",
        emphasized && "font-semibold bg-white/[0.06]",
      )}
    >
      {children}
    </button>
  );
}

function ManagePrizeTile({
  tier,
  totalActive,
  onBodyClick,
  onStash,
  onPromote,
  onAdd,
  onMore,
  disableStash,
  disablePromote,
  disableAdd,
}: ManagePrizeTileProps) {
  const stripe = tierClassColor(tier.label);
  const productName = tier.linkedProductName?.trim();
  const displayName = productName || tier.label;
  const isEmpty = tier.activeCount === 0 && tier.inactiveCount === 0;

  return (
    <div
      className={cn(
        "relative flex flex-col rounded-[10px] border border-border bg-card overflow-hidden",
        isEmpty && "opacity-65",
      )}
    >
      <div className="h-[3px]" style={{ background: stripe }} aria-hidden />

      <button
        type="button"
        onClick={onBodyClick}
        className="flex flex-col gap-2 px-3 py-2.5 text-left hover:bg-accent/40 transition-colors"
      >
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <div className="text-[13px] leading-snug text-foreground line-clamp-2">
              {displayName}
            </div>
            <div
              className="text-[10px] uppercase tracking-[0.06em] font-medium mt-1.5"
              style={{ color: stripe }}
            >
              {tier.label}
            </div>
          </div>
          <div className="text-sm tabular-nums text-muted-foreground shrink-0">
            {formatMoney(tier.price)}
          </div>
        </div>

        <div className="grid grid-cols-3 gap-1 pt-2 text-center">
          <Stat value={tier.activeCount} label="active" />
          <Stat
            value={tier.inactiveCount}
            label="inactive"
            amber={tier.inactiveCount > 0}
          />
          <Stat
            value={formatChance(tier.activeCount, totalActive)}
            label="chance"
            raw
          />
        </div>
      </button>

      <div className="mt-auto border-t border-border p-2">
        <div className="flex items-center gap-1">
          <ActionButton onClick={onStash} disabled={disableStash} title="Stash">
            <ChevronDown className="h-3.5 w-3.5" />
            <span className="text-[11px]">Stash</span>
          </ActionButton>
          <ActionButton
            onClick={onPromote}
            disabled={disablePromote}
            title="Promote"
            accentColor={tier.inactiveCount > 0 && !disablePromote ? "#f5b942" : undefined}
          >
            <ChevronUp className="h-3.5 w-3.5" />
            <span className="text-[11px]">Promote</span>
          </ActionButton>
          <ActionButton
            onClick={onAdd}
            disabled={disableAdd}
            title="Transfer in"
            accentColor={stripe}
          >
            <Plus className="h-3.5 w-3.5" />
            <span className="text-[11px]">Add</span>
          </ActionButton>
          <ActionButton onClick={onMore} title="More">
            <MoreHorizontal className="h-3.5 w-3.5" />
          </ActionButton>
        </div>
      </div>
    </div>
  );
}

function Stat({
  value,
  label,
  amber,
  raw,
}: {
  value: number | string;
  label: string;
  amber?: boolean;
  raw?: boolean;
}) {
  return (
    <div className="flex flex-col items-center">
      <div
        className={cn(
          "text-base font-semibold tabular-nums",
          amber ? "text-amber-500" : "text-foreground",
        )}
      >
        {raw ? value : (value as number).toLocaleString()}
      </div>
      <div className="text-[10px] uppercase tracking-wide text-muted-foreground">
        {label}
      </div>
    </div>
  );
}

function ActionButton({
  onClick,
  disabled,
  title,
  accentColor,
  children,
}: {
  onClick: () => void;
  disabled?: boolean;
  title?: string;
  accentColor?: string;
  children: React.ReactNode;
}) {
  const style: React.CSSProperties | undefined = accentColor
    ? { color: accentColor, borderColor: `${accentColor}55` }
    : undefined;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      style={style}
      className={cn(
        "inline-flex flex-1 items-center justify-center gap-1 rounded-md border border-border bg-transparent h-9 px-1 transition-colors",
        disabled
          ? "opacity-40 cursor-not-allowed"
          : "hover:bg-accent text-foreground",
      )}
    >
      {children}
    </button>
  );
}
