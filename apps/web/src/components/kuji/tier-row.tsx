"use client";

import { Minus, Plus, MoreHorizontal, ChevronDown, ChevronUp } from "lucide-react";
import type { KujiBoxTier } from "@/types/api";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { formatMoney } from "@/lib/utils/format-money";
import { formatChance, tierClassColor } from "./kuji-tier-class";

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
  readonly onMore: () => void;
  readonly disableStash?: boolean;
  readonly disablePromote?: boolean;
  readonly disableAdd?: boolean;
}

export type TierRowProps = RecordDrawRowProps | ManagePrizeRowProps;

export function TierRow(props: TierRowProps) {
  const { tier, totalActive, onBodyClick } = props;
  const stripe = tierClassColor(tier.label);
  const isEmpty = tier.activeCount === 0 && tier.inactiveCount === 0;
  const productName = tier.linkedProductName?.trim();
  const displayName = productName || tier.label;

  const selected = props.mode === "recordDraw" && props.count > 0;
  const containerStyle: React.CSSProperties | undefined = selected
    ? { background: `${stripe}1f`, borderColor: `${stripe}66` }
    : undefined;

  return (
    <div
      style={containerStyle}
      className={cn(
        "relative flex flex-col rounded-lg border border-border bg-card overflow-hidden transition-colors",
        isEmpty && "opacity-65",
      )}
    >
      <div className="flex items-stretch">
        <div
          className="w-[3px] shrink-0 self-stretch"
          style={{ background: stripe }}
          aria-hidden
        />
        <button
          type="button"
          onClick={onBodyClick}
          className="flex-1 min-w-0 px-3 py-2.5 text-left transition-colors hover:bg-accent/30"
        >
          <div className="flex items-start gap-2">
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium text-foreground line-clamp-2">
                {displayName}
              </div>
              <div className="mt-0.5 flex items-center gap-2 text-xs">
                <span
                  className="font-medium uppercase tracking-wide"
                  style={{ color: stripe }}
                >
                  {tier.label}
                </span>
                {props.mode === "managePrizes" ? null : (
                  <RecordDrawHint
                    count={props.count}
                    activeCount={tier.activeCount}
                  />
                )}
              </div>
              {props.mode === "managePrizes" ? (
                <div className="mt-1 text-xs text-muted-foreground tabular-nums">
                  <span className="text-foreground font-medium">
                    {tier.activeCount.toLocaleString()}
                  </span>{" "}
                  active ·{" "}
                  <span
                    className={cn(
                      "font-medium",
                      tier.inactiveCount > 0
                        ? "text-amber-500"
                        : "text-foreground",
                    )}
                  >
                    {tier.inactiveCount.toLocaleString()}
                  </span>{" "}
                  inactive · {formatChance(tier.activeCount, totalActive)} chance
                </div>
              ) : null}
            </div>
          </div>
        </button>

        <div className="flex items-center pr-2 shrink-0">
          {props.mode === "recordDraw" ? (
            props.count > 0 ? (
              <span
                className="rounded-full px-2 py-1 text-xs font-semibold text-white tabular-nums"
                style={{ background: stripe }}
              >
                ×{props.count}
              </span>
            ) : (
              <button
                type="button"
                onClick={props.onAddOne}
                aria-label="Add one"
                className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border text-foreground transition-colors hover:bg-accent"
              >
                <Plus className="h-4 w-4" />
              </button>
            )
          ) : (
            <div className="text-sm tabular-nums text-muted-foreground">
              {formatMoney(tier.price)}
            </div>
          )}
        </div>
      </div>

      {props.mode === "recordDraw" && props.count > 0 ? (
        <div className="border-t border-border/60 p-2">
          <RecordDrawMobileStepper
            stripe={stripe}
            onDecrement={props.onDecrement}
            onAddFive={props.onAddFive}
            onAddTen={props.onAddTen}
            onAddOne={props.onAddOne}
          />
        </div>
      ) : null}

      {props.mode === "managePrizes" ? (
        <div className="border-t border-border p-2">
          <ManagePrizeActions
            onStash={props.onStash}
            onPromote={props.onPromote}
            onAdd={props.onAdd}
            onMore={props.onMore}
            disableStash={props.disableStash}
            disablePromote={props.disablePromote}
            disableAdd={props.disableAdd}
            stripe={stripe}
            promoteAmber={tier.inactiveCount > 0}
          />
        </div>
      ) : null}
    </div>
  );
}

function RecordDrawHint({
  count,
  activeCount,
}: {
  count: number;
  activeCount: number;
}) {
  if (count === 0) {
    return (
      <span className="text-muted-foreground tabular-nums">
        · {activeCount.toLocaleString()} left
      </span>
    );
  }
  const after = activeCount - count;
  const over = after < 0;
  return (
    <span
      className={cn(
        "tabular-nums",
        over ? "text-destructive" : "text-muted-foreground",
      )}
    >
      · {after.toLocaleString()} after draw
    </span>
  );
}

function RecordDrawMobileStepper({
  stripe,
  onDecrement,
  onAddFive,
  onAddTen,
  onAddOne,
}: {
  stripe: string;
  onDecrement: () => void;
  onAddFive: () => void;
  onAddTen: () => void;
  onAddOne: () => void;
}) {
  return (
    <div className="flex items-center gap-1">
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
  );
}

function ManagePrizeActions({
  onStash,
  onPromote,
  onAdd,
  onMore,
  disableStash,
  disablePromote,
  disableAdd,
  stripe,
  promoteAmber,
}: {
  onStash: () => void;
  onPromote: () => void;
  onAdd: () => void;
  onMore: () => void;
  disableStash?: boolean;
  disablePromote?: boolean;
  disableAdd?: boolean;
  stripe: string;
  promoteAmber: boolean;
}) {
  return (
    <div className="flex items-center gap-1">
      <ActionButton onClick={onStash} disabled={disableStash} title="Stash">
        <ChevronDown className="h-3.5 w-3.5" />
        <span className="text-[11px]">Stash</span>
      </ActionButton>
      <ActionButton
        onClick={onPromote}
        disabled={disablePromote}
        title="Promote"
        accentColor={promoteAmber && !disablePromote ? "#f5b942" : undefined}
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
