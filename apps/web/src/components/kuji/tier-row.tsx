"use client";

import { Minus, Plus, ArrowDown, ArrowUp } from "lucide-react";
import type { KujiBoxTier } from "@/types/api";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { formatMoney } from "@/lib/utils/format-money";
import { formatChance } from "./kuji-tier-class";
import { useTierClassColor } from "./tier-class-color-context";
import { hexWithAlpha } from "./tier-palette";

interface BaseProps {
  readonly tier: KujiBoxTier;
  readonly totalActive: number;
  readonly onBodyClick?: () => void;
}

interface RecordDrawRowProps extends BaseProps {
  readonly mode: "recordDraw";
  readonly count: number;
  readonly onDecrement: () => void;
  readonly onAddOne: () => void;
  readonly onAddFive: () => void;
  readonly onAddTen: () => void;
}

interface ManagePrizeRowProps extends BaseProps {
  readonly mode: "managePrizes";
  readonly onStash: () => void;
  readonly onPromote: () => void;
  readonly onAdd: () => void;
  readonly disableStash?: boolean;
  readonly disablePromote?: boolean;
  readonly disableAdd?: boolean;
}

export type TierRowProps = RecordDrawRowProps | ManagePrizeRowProps;

export function TierRow(props: TierRowProps) {
  if (props.mode === "recordDraw") {
    return <RecordDrawRow {...props} />;
  }
  return <ManagePrizeRow {...props} />;
}

function RecordDrawRow({
  tier,
  count,
  onBodyClick,
  onDecrement,
  onAddOne,
  onAddFive,
  onAddTen,
}: RecordDrawRowProps) {
  const stripe = useTierClassColor(tier.label);
  const isEmpty = tier.activeCount === 0 && tier.inactiveCount === 0;
  const productName = tier.linkedProductName?.trim();
  const displayName = productName || tier.label;
  const selected = count > 0;

  const containerStyle: React.CSSProperties = {
    background: selected
      ? `linear-gradient(180deg, ${hexWithAlpha(stripe, 0.18)} 0%, var(--card) 100%)`
      : undefined,
    borderColor: selected ? hexWithAlpha(stripe, 0.7) : undefined,
  };

  return (
    <div
      style={containerStyle}
      className={cn(
        "relative flex flex-col rounded-[12px] border border-border bg-card overflow-hidden transition-colors",
        isEmpty && "opacity-65",
      )}
    >
      <div
        role="button"
        tabIndex={0}
        onClick={onBodyClick}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onBodyClick?.();
          }
        }}
        className="flex items-stretch gap-2.5 pr-3 pl-2 py-2.5 cursor-pointer"
      >
        <div
          className="w-[3px] rounded-full"
          style={{ background: stripe }}
          aria-hidden
        />
        <div className="flex-1 min-w-0">
          <div className="text-[13.5px] leading-[1.25] text-foreground line-clamp-2">
            {displayName}
          </div>
          <div className="mt-1 flex items-baseline gap-2 text-[11px]">
            <span
              className="uppercase tracking-[0.06em] font-medium"
              style={{ color: stripe }}
            >
              {tier.label}
            </span>
            <span className="text-muted-foreground tabular-nums">
              {selected
                ? `· ${Math.max(0, tier.activeCount - count).toLocaleString()} after`
                : `· ${tier.activeCount.toLocaleString()} left`}
            </span>
          </div>
        </div>
        {selected ? (
          <span
            className="self-start rounded-full px-2 py-0.5 text-xs font-semibold text-white tabular-nums"
            style={{ background: stripe }}
          >
            ×{count}
          </span>
        ) : (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onAddOne();
            }}
            aria-label="Add one"
            className="self-center inline-flex h-9 w-9 items-center justify-center rounded-md border border-border text-foreground hover:bg-accent transition-colors"
          >
            <Plus className="h-4 w-4" />
          </button>
        )}
      </div>

      {selected ? (
        <div className="flex items-center gap-1 px-2 pb-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-9 flex-1 px-0"
            onClick={onDecrement}
            aria-label="Remove one"
          >
            <Minus className="h-3.5 w-3.5" />
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-9 flex-1 px-0 tabular-nums"
            onClick={onAddFive}
          >
            +5
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-9 flex-1 px-0 tabular-nums"
            onClick={onAddTen}
          >
            +10
          </Button>
          <button
            type="button"
            onClick={onAddOne}
            aria-label="Add one"
            style={{ background: stripe }}
            className="h-9 flex-1 inline-flex items-center justify-center rounded-md text-white font-medium"
          >
            <Plus className="h-4 w-4" />
          </button>
        </div>
      ) : null}
    </div>
  );
}

function ManagePrizeRow({
  tier,
  totalActive,
  onBodyClick,
  onStash,
  onPromote,
  onAdd,
  disableStash,
  disablePromote,
  disableAdd,
}: ManagePrizeRowProps) {
  const stripe = useTierClassColor(tier.label);
  const isEmpty = tier.activeCount === 0 && tier.inactiveCount === 0;
  const productName = tier.linkedProductName?.trim();
  const displayName = productName || tier.label;
  const hasInactive = tier.inactiveCount > 0;

  return (
    <div
      className={cn(
        "relative flex flex-col rounded-[12px] border border-border bg-card overflow-hidden",
        isEmpty && "opacity-65",
      )}
    >
      <button
        type="button"
        onClick={onBodyClick}
        className="flex items-stretch gap-2.5 pr-3 pl-2 pt-2.5 pb-2 text-left transition-colors"
      >
        <span
          className="w-[3px] self-stretch rounded-full"
          style={{ background: stripe }}
          aria-hidden
        />
        <div className="flex-1 min-w-0">
          <div className="text-[13.5px] leading-[1.25] text-foreground line-clamp-2">
            {displayName}
          </div>
          <div className="mt-[3px] flex items-baseline gap-2 text-[11px]">
            <span
              className="uppercase tracking-[0.06em] font-medium"
              style={{ color: stripe }}
            >
              {tier.label}
            </span>
            <span className="text-muted-foreground tabular-nums">
              ·{" "}
              {tier.price != null && Number.isFinite(tier.price) ? (
                formatMoney(tier.price)
              ) : (
                <span className="text-white/30">no price</span>
              )}
            </span>
          </div>
          <div className="mt-1.5 flex items-baseline gap-3 text-[11.5px] tabular-nums">
            <span>
              <span className="font-medium text-foreground">
                {tier.activeCount.toLocaleString()}
              </span>
              <span className="text-muted-foreground"> active</span>
            </span>
            <span
              className={cn(
                hasInactive ? "text-amber-500" : "text-muted-foreground",
              )}
            >
              <span className={hasInactive ? "font-medium" : ""}>
                {tier.inactiveCount.toLocaleString()}
              </span>{" "}
              inactive
            </span>
            <span className="text-muted-foreground ml-auto">
              {formatChance(tier.activeCount, totalActive)} chance
            </span>
          </div>
        </div>
      </button>

      <div className="flex items-stretch border-t border-border">
        <RowAction onClick={onStash} disabled={disableStash} label="Stash">
          <ArrowDown className="h-4 w-4" strokeWidth={1.75} />
        </RowAction>
        <RowAction
          onClick={onPromote}
          disabled={disablePromote}
          label="Promote"
          accentColor={hasInactive && !disablePromote ? "#f5b942" : undefined}
        >
          <ArrowUp className="h-4 w-4" strokeWidth={1.75} />
        </RowAction>
        <RowAction
          onClick={onAdd}
          disabled={disableAdd}
          label="Add"
          accentColor={stripe}
        >
          <Plus className="h-4 w-4" strokeWidth={1.75} />
        </RowAction>
      </div>
    </div>
  );
}

function RowAction({
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
        "inline-flex flex-1 items-center justify-center gap-2 py-3.5 px-1 text-[13.5px] font-medium border-l border-border first:border-l-0 transition-colors",
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
