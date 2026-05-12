"use client";

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
  const tiers = [...box.tiers].sort(compareTiers);
  const isOpen = box.status === KujiBoxStatus.OPEN;
  const showActions = canEditStructural && isOpen;

  return (
    <div className="overflow-x-auto">
      <Table className="w-full">
        <TableHeader>
          <TableRow>
            <TableHead>Label</TableHead>
            <TableHead>Linked Product</TableHead>
            <TableHead className="hidden md:table-cell text-right w-24">In box</TableHead>
            <TableHead className="text-right w-24">Price</TableHead>
            <TableHead className="hidden md:table-cell text-right w-20">Slips</TableHead>
            <TableHead className="hidden md:table-cell text-right w-20">Chance</TableHead>
            {showActions ? (
              <TableHead className="w-32 text-right">Actions</TableHead>
            ) : null}
          </TableRow>
        </TableHeader>
        <TableBody>
          {tiers.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={showActions ? 7 : 6}
                className="text-center text-muted-foreground py-6"
              >
                No tiers configured
              </TableCell>
            </TableRow>
          ) : (
            tiers.map((tier) => {
              const inventoryInBox = tier.linkedInventoryAtBoxLocation ?? 0;
              const heldBack =
                tier.linkedProductId != null && inventoryInBox > tier.count
                  ? inventoryInBox - tier.count
                  : 0;
              return (
                <TableRow key={tier.id}>
                  <TableCell className="font-medium">
                    {tier.letter ? (
                      <span className="font-mono mr-2 text-muted-foreground">{tier.letter}</span>
                    ) : null}
                    {tier.label}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {tier.linkedProductName ?? "—"}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-right tabular-nums text-muted-foreground">
                    {tier.linkedProductId == null
                      ? "—"
                      : inventoryInBox.toLocaleString()}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCurrency(tier.price)}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-right tabular-nums">
                    {heldBack > 0 ? (
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span className="text-amber-600 dark:text-amber-500 cursor-help">
                            {tier.count.toLocaleString()}
                          </span>
                        </TooltipTrigger>
                        <TooltipContent>
                          {heldBack} held back
                        </TooltipContent>
                      </Tooltip>
                    ) : (
                      tier.count.toLocaleString()
                    )}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-right tabular-nums text-muted-foreground">
                    {formatChance(tier.count, box.totalCount)}
                  </TableCell>
                  {showActions ? (
                    <TableCell className="text-right">
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
            })
          )}
        </TableBody>
      </Table>
    </div>
  );
}
