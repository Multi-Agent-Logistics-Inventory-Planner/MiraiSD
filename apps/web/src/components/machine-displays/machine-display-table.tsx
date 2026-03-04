"use client";

import { useMemo } from "react";
import { format } from "date-fns";
import { AlertTriangle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { MachineDisplay, LOCATION_TYPE_LABELS } from "@/types/api";
import { cn } from "@/lib/utils";

/** Grouped machine display - one entry per machine with multiple products */
export interface GroupedMachineDisplay {
  machineId: string;
  machineCode: string;
  locationType: MachineDisplay["locationType"];
  displays: MachineDisplay[];
  /** Max days active across all products */
  maxDaysActive: number;
  /** True if any product is stale */
  hasStaleProducts: boolean;
  /** Earliest start date */
  earliestStartedAt: string;
  /** Most recent actor */
  actorName: string | null;
}

interface MachineDisplayTableProps {
  data: MachineDisplay[];
  isLoading: boolean;
  onRowClick?: (display: MachineDisplay) => void;
}

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 8 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell className="rounded-l-lg">
            <Skeleton className="h-4 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-48" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell className="rounded-r-lg">
            <Skeleton className="h-4 w-28" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

function formatDaysActive(days: number): React.ReactNode {
  if (days === 0) {
    return <span className="text-muted-foreground">Today</span>;
  }
  if (days === 1) {
    return <span>1 day</span>;
  }
  return <span>{days} days</span>;
}

function getStaleBadgeVariant(
  days: number,
  stale: boolean
): "default" | "secondary" | "destructive" | "outline" {
  if (stale) {
    return days >= 21 ? "destructive" : "secondary";
  }
  return "outline";
}

/** Group displays by machine */
function groupByMachine(displays: MachineDisplay[]): GroupedMachineDisplay[] {
  const grouped = new Map<string, GroupedMachineDisplay>();

  for (const display of displays) {
    const key = `${display.locationType}:${display.machineId}`;

    if (!grouped.has(key)) {
      grouped.set(key, {
        machineId: display.machineId,
        machineCode: display.machineCode,
        locationType: display.locationType,
        displays: [],
        maxDaysActive: 0,
        hasStaleProducts: false,
        earliestStartedAt: display.startedAt,
        actorName: display.actorName,
      });
    }

    const group = grouped.get(key)!;
    group.displays.push(display);
    group.maxDaysActive = Math.max(group.maxDaysActive, display.daysActive);
    group.hasStaleProducts = group.hasStaleProducts || display.stale;

    if (new Date(display.startedAt) < new Date(group.earliestStartedAt)) {
      group.earliestStartedAt = display.startedAt;
    }
  }

  // Sort by max days active (most stale first)
  return Array.from(grouped.values()).sort(
    (a, b) => b.maxDaysActive - a.maxDaysActive
  );
}

export function MachineDisplayTable({
  data,
  isLoading,
  onRowClick,
}: MachineDisplayTableProps) {
  const groupedData = useMemo(() => groupByMachine(data), [data]);

  return (
    <Table>
      <DataTableHeader>
        <TableHead className="w-24 rounded-l-lg">Machine</TableHead>
        <TableHead>Products</TableHead>
        <TableHead>Type</TableHead>
        <TableHead className="text-center">Days Active</TableHead>
        <TableHead>Set By</TableHead>
        <TableHead className="rounded-r-lg">Started</TableHead>
      </DataTableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton />
        ) : groupedData.length === 0 ? (
          <TableRow>
            <TableCell
              colSpan={6}
              className="h-24 text-center text-muted-foreground"
            >
              No machine displays found.
            </TableCell>
          </TableRow>
        ) : (
          groupedData.map((group) => (
            <TableRow
              key={`${group.locationType}:${group.machineId}`}
              className={cn(
                onRowClick && "cursor-pointer hover:bg-muted/50",
                group.hasStaleProducts && "bg-amber-50/50 dark:bg-amber-950/20"
              )}
              onClick={() => onRowClick?.(group.displays[0])}
            >
              <TableCell className="font-mono font-medium rounded-l-lg">
                <div className="flex items-center gap-2">
                  {group.machineCode}
                  {group.hasStaleProducts && (
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger>
                          <AlertTriangle className="h-4 w-4 text-amber-500" />
                        </TooltipTrigger>
                        <TooltipContent>
                          <p>Has stale products - consider swapping</p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  )}
                </div>
              </TableCell>
              <TableCell>
                <div className="flex flex-wrap gap-1">
                  {group.displays.map((display) => (
                    <TooltipProvider key={display.id}>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Badge
                            variant={display.stale ? "secondary" : "outline"}
                            className={cn(
                              "text-xs",
                              display.stale && "border-amber-300 bg-amber-100 dark:bg-amber-900"
                            )}
                          >
                            {display.productName}
                          </Badge>
                        </TooltipTrigger>
                        <TooltipContent>
                          <p>{display.productSku} · {display.daysActive} days</p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  ))}
                </div>
              </TableCell>
              <TableCell>
                <span className="text-sm text-muted-foreground">
                  {LOCATION_TYPE_LABELS[group.locationType]}
                </span>
              </TableCell>
              <TableCell className="text-center">
                <Badge
                  variant={getStaleBadgeVariant(
                    group.maxDaysActive,
                    group.hasStaleProducts
                  )}
                >
                  {formatDaysActive(group.maxDaysActive)}
                </Badge>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {group.actorName ?? "-"}
              </TableCell>
              <TableCell className="text-muted-foreground whitespace-nowrap rounded-r-lg">
                {format(new Date(group.earliestStartedAt), "MMM d, yyyy")}
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
