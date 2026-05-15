"use client";

import { Plus, MoreHorizontal, ChevronDown, ChevronUp } from "lucide-react";
import type { KujiBoxTier } from "@/types/api";
import { cn } from "@/lib/utils";
import { formatMoney } from "@/lib/utils/format-money";
import { hexWithAlpha } from "./tier-palette";
import { formatChance } from "./kuji-tier-class";
import { useTierClassColor } from "./tier-class-color-context";

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
  const color = useTierClassColor(tier.label);
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
  const stripe = useTierClassColor(tier.label);
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
        className="px-3 pt-2.5 pb-1.5 text-left hover:bg-accent/40 transition-colors"
      >
        <div className="text-[13px] leading-snug text-foreground line-clamp-2 min-h-[34px]">
          {displayName}
        </div>
        <div className="mt-1 flex items-baseline justify-between">
          <span
            className="text-[10px] uppercase tracking-[0.06em] font-medium"
            style={{ color: stripe }}
          >
            {tier.label}
          </span>
          <span className="text-[11px] tabular-nums">
            {tier.price != null && Number.isFinite(tier.price) ? (
              <span className="text-foreground/80">{formatMoney(tier.price)}</span>
            ) : (
              <span className="text-white/30">no price</span>
            )}
          </span>
        </div>
      </button>

      <div className="px-3 py-2 grid grid-cols-3 gap-1 items-baseline">
        <Stat value={tier.activeCount} label="active" emphasized />
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

      <div className="mt-auto flex items-stretch border-t border-border">
        <FlatActionBtn onClick={onStash} disabled={disableStash} label="Stash">
          <ChevronDown className="h-3 w-3" />
        </FlatActionBtn>
        <FlatActionBtn
          onClick={onPromote}
          disabled={disablePromote}
          label="Promote"
          accentColor={tier.inactiveCount > 0 && !disablePromote ? "#f5b942" : undefined}
        >
          <ChevronUp className="h-3 w-3" />
        </FlatActionBtn>
        <FlatActionBtn
          onClick={onAdd}
          disabled={disableAdd}
          label="Add"
          accentColor={stripe}
        >
          <Plus className="h-3 w-3" />
        </FlatActionBtn>
        <FlatActionBtn onClick={onMore} label="" title="More">
          <MoreHorizontal className="h-3.5 w-3.5" />
        </FlatActionBtn>
      </div>
    </div>
  );
}

function Stat({
  value,
  label,
  amber,
  emphasized,
  raw,
}: {
  value: number | string;
  label: string;
  amber?: boolean;
  emphasized?: boolean;
  raw?: boolean;
}) {
  const isZero = !raw && typeof value === "number" && value === 0;
  const valueColor = amber
    ? "text-amber-500"
    : isZero
      ? "text-white/35"
      : "text-foreground";
  return (
    <div className="text-center">
      <div
        className={cn(
          "font-medium tabular-nums leading-[1.1]",
          emphasized ? "text-lg" : "text-[15px]",
          valueColor,
        )}
      >
        {raw ? value : (value as number).toLocaleString()}
      </div>
      <div className="text-[9.5px] uppercase tracking-[0.04em] text-muted-foreground mt-0.5">
        {label}
      </div>
    </div>
  );
}

function FlatActionBtn({
  onClick,
  disabled,
  label,
  title,
  accentColor,
  children,
}: {
  onClick: () => void;
  disabled?: boolean;
  label: string;
  title?: string;
  accentColor?: string;
  children: React.ReactNode;
}) {
  const style: React.CSSProperties | undefined =
    accentColor && !disabled ? { color: accentColor } : undefined;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title ?? label ?? "More"}
      style={style}
      className={cn(
        "inline-flex items-center justify-center gap-1 py-2 px-1 text-[11px] font-medium border-l border-border first:border-l-0 transition-colors",
        label ? "flex-1" : "shrink-0 px-3",
        disabled
          ? "text-white/20 cursor-not-allowed"
          : "text-muted-foreground hover:bg-white/[0.04] hover:text-foreground",
      )}
    >
      {children}
      {label}
    </button>
  );
}
