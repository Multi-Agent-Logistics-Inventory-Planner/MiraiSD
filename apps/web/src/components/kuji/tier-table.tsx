"use client";

import { useMemo, useState } from "react";
import { Pencil, ArrowDownToLine } from "lucide-react";
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
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import type { KujiBox, KujiBoxTier } from "@/types/api";
import { KujiBoxStatus } from "@/types/api";
import { compareTiers } from "./tier-palette";

interface TierTableProps {
  readonly box: KujiBox;
  readonly canEditStructural: boolean;
  readonly onTransferIn: (tier: KujiBoxTier) => void;
  readonly onEditTier: (tier: KujiBoxTier) => void;
}

interface TierGroup {
  label: string;
  letter?: string | null;
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
}: TierTableProps) {
  const isOpen = box.status === KujiBoxStatus.OPEN;
  const showActions = canEditStructural && isOpen;

  const groups = useMemo<TierGroup[]>(() => {
    const sorted = [...box.tiers].sort(compareTiers);
    const acc: TierGroup[] = [];
    for (const tier of sorted) {
      const last = acc[acc.length - 1];
      if (last && last.label === tier.label) {
        last.tiers.push(tier);
      } else {
        acc.push({ label: tier.label, letter: tier.letter, tiers: [tier] });
      }
    }
    return acc;
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
    const inventoryInBox = tier.linkedInventoryAtBoxLocation ?? 0;
    const heldBack =
      tier.linkedProductId != null && inventoryInBox > tier.count
        ? inventoryInBox - tier.count
        : 0;
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
        <TableCell className="hidden md:table-cell text-right tabular-nums text-muted-foreground w-24">
          {tier.linkedProductId == null
            ? "—"
            : inventoryInBox.toLocaleString()}
        </TableCell>
        <TableCell className="text-right tabular-nums w-24">
          {formatCurrency(tier.price)}
        </TableCell>
        <TableCell className="hidden md:table-cell text-right tabular-nums w-20">
          {heldBack > 0 ? (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="text-amber-600 dark:text-amber-500 cursor-help">
                  {tier.count.toLocaleString()}
                </span>
              </TooltipTrigger>
              <TooltipContent>{heldBack} held back</TooltipContent>
            </Tooltip>
          ) : (
            tier.count.toLocaleString()
          )}
        </TableCell>
        <TableCell className="hidden md:table-cell text-right tabular-nums text-muted-foreground w-20">
          {formatChance(tier.count, box.totalCount)}
        </TableCell>
        {showActions ? (
          <TableCell className="text-right w-24">
            <div className="flex items-center justify-end gap-1">
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
          <TableHead className="hidden md:table-cell text-right w-24">In box</TableHead>
          <TableHead className="text-right w-24">Price</TableHead>
          <TableHead className="hidden md:table-cell text-right w-20">Slips</TableHead>
          <TableHead className="hidden md:table-cell text-right w-20">Chance</TableHead>
          {showActions ? (
            <TableHead className="w-24 text-right">Actions</TableHead>
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
      <div className="relative">
        <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />
        <TabsList className="flex max-w-full justify-start overflow-x-auto scrollbar-none pr-6">
          {groups.map((group) => (
            <TabsTrigger
              key={group.label}
              value={group.label}
              className="flex-none whitespace-nowrap"
            >
              {group.letter ? (
                <span className="font-mono mr-1.5 opacity-70">
                  {group.letter}
                </span>
              ) : null}
              {group.label}
              <span className="ml-1.5 text-xs opacity-70">
                ({group.tiers.length})
              </span>
            </TabsTrigger>
          ))}
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
