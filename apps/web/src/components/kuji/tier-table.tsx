"use client";

import { useMemo, useState } from "react";
import {
  Pencil,
  ArrowDownToLine,
  PauseCircle,
  PlayCircle,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { KujiBox, KujiBoxTier } from "@/types/api";
import { KujiBoxStatus } from "@/types/api";
import { compareTiers } from "./tier-palette";

interface TierTableProps {
  readonly box: KujiBox;
  readonly canEditStructural: boolean;
  readonly onTransferIn: (tier: KujiBoxTier) => void;
  readonly onEditTier: (tier: KujiBoxTier) => void;
  /** Called to move 1 slip between buckets. Direction "DEACTIVATE" moves active→inactive. */
  readonly onMoveSlip?: (tier: KujiBoxTier, direction: "ACTIVATE" | "DEACTIVATE") => void;
  /** Called to delete a single prize from the active bucket. */
  readonly onDeletePrize?: (tier: KujiBoxTier) => void;
}

interface TierGroup {
  label: string;
  tiers: KujiBoxTier[];
}

function formatCurrency(value?: number | null): string {
  if (value == null) return "—";
  return `$${value.toFixed(2)}`;
}

function formatChance(count: number, totalCount: number): string {
  if (totalCount <= 0) return "0.0%";
  const pct = (count / totalCount) * 100;
  return `${pct.toFixed(1)}%`;
}

export function TierTable({
  box,
  canEditStructural,
  onTransferIn,
  onEditTier,
  onMoveSlip,
  onDeletePrize,
}: TierTableProps) {
  const isOpen = box.status === KujiBoxStatus.OPEN;
  const showActions = canEditStructural && isOpen;
  const showSlipActions =
    isOpen && (onMoveSlip != null || onDeletePrize != null);

  const groups = useMemo<TierGroup[]>(() => {
    const sorted = [...box.tiers].sort(compareTiers);
    const byLabel = new Map<string, TierGroup>();
    for (const tier of sorted) {
      const existing = byLabel.get(tier.label);
      if (existing) {
        existing.tiers.push(tier);
      } else {
        byLabel.set(tier.label, {
          label: tier.label,
          tiers: [tier],
        });
      }
    }
    return Array.from(byLabel.values());
  }, [box.tiers]);

  const [activeLabel, setActiveLabel] = useState<string>(
    () => groups[0]?.label ?? "",
  );

  if (groups.length === 0) {
    return (
      <div className="text-center text-muted-foreground py-6">
        No tiers configured
      </div>
    );
  }

  const activeGroup =
    groups.find((g) => g.label === activeLabel) ?? groups[0];

  const renderTierRow = (tier: KujiBoxTier) => {
    return (
      <TableRow key={tier.id}>
        <TableCell className="max-w-0 text-muted-foreground">
          <span
            className="block truncate"
            title={tier.linkedProductName ?? undefined}
          >
            {tier.linkedProductName ?? "—"}
          </span>
        </TableCell>
        <TableCell className="text-right tabular-nums w-24">
          {formatCurrency(tier.price)}
        </TableCell>
        <TableCell className="hidden md:table-cell text-right tabular-nums w-20">
          {tier.activeCount.toLocaleString()}
        </TableCell>
        <TableCell className="hidden md:table-cell text-right tabular-nums w-20 text-muted-foreground">
          {tier.inactiveCount.toLocaleString()}
        </TableCell>
        <TableCell className="hidden md:table-cell text-right tabular-nums text-muted-foreground w-20">
          {formatChance(tier.activeCount, box.totalCount)}
        </TableCell>
        {showActions || showSlipActions ? (
          <TableCell className="text-right w-44">
            <div className="flex items-center justify-end gap-1">
              {showSlipActions ? (
                <>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() => onMoveSlip?.(tier, "DEACTIVATE")}
                    disabled={tier.activeCount === 0 || !onMoveSlip}
                    title="Deactivate one slip (move to inactive)"
                    aria-label={`Deactivate slip in ${tier.label}`}
                  >
                    <PauseCircle className="h-4 w-4" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() => onMoveSlip?.(tier, "ACTIVATE")}
                    disabled={tier.inactiveCount === 0 || !onMoveSlip}
                    title="Activate one slip (move to active)"
                    aria-label={`Activate slip in ${tier.label}`}
                  >
                    <PlayCircle className="h-4 w-4" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() => onDeletePrize?.(tier)}
                    disabled={!onDeletePrize}
                    title="Delete this prize (removes the tier)"
                    aria-label={`Delete prize ${tier.label}`}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </>
              ) : null}
              {showActions ? (
                <>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() => onEditTier(tier)}
                    title="Edit tier"
                    aria-label={`Edit tier ${tier.label}`}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() => onTransferIn(tier)}
                    title="Transfer"
                    aria-label={`Transfer for tier ${tier.label}`}
                  >
                    <ArrowDownToLine className="h-4 w-4" />
                  </Button>
                </>
              ) : null}
            </div>
          </TableCell>
        ) : null}
      </TableRow>
    );
  };

  const renderTable = (group: TierGroup) => (
    <Table className="w-full table-fixed">
      <TableHeader>
        <TableRow>
          <TableHead>Linked Product</TableHead>
          <TableHead className="text-right w-24">Price</TableHead>
          <TableHead className="hidden md:table-cell text-right w-20">
            Active
          </TableHead>
          <TableHead className="hidden md:table-cell text-right w-20">
            Inactive
          </TableHead>
          <TableHead className="hidden md:table-cell text-right w-20">
            Chance
          </TableHead>
          {showActions || showSlipActions ? (
            <TableHead className="w-44 text-right">Actions</TableHead>
          ) : null}
        </TableRow>
      </TableHeader>
      <TableBody>{group.tiers.map(renderTierRow)}</TableBody>
    </Table>
  );

  return (
    <Tabs
      value={activeGroup.label}
      onValueChange={setActiveLabel}
      className="w-full"
    >
      <div className="relative max-w-full overflow-x-auto scrollbar-none">
        <TabsList className="justify-start">
          {groups.map((group) => {
            const groupActive = group.tiers.reduce(
              (sum, t) => sum + t.activeCount,
              0,
            );
            const groupInactive = group.tiers.reduce(
              (sum, t) => sum + t.inactiveCount,
              0,
            );
            return (
              <TabsTrigger
                key={group.label}
                value={group.label}
                className="flex-none whitespace-nowrap"
              >
                {group.label}
                <span className="ml-1.5 text-xs opacity-70">
                  ({group.tiers.length} · {formatChance(groupActive, box.totalCount)}
                  {groupInactive > 0 ? ` + ${groupInactive} inactive` : ""})
                </span>
              </TabsTrigger>
            );
          })}
        </TabsList>
      </div>
      {groups.map((group) => (
        <TabsContent key={group.label} value={group.label} className="mt-3">
          {renderTable(group)}
        </TabsContent>
      ))}
    </Tabs>
  );
}
