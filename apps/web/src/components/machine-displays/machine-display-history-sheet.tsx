"use client";

import { format } from "date-fns";
import { History, X } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { ScrollArea } from "@/components/ui/scroll-area";
import { MachineDisplay, LOCATION_TYPE_LABELS } from "@/types/api";
import { useMachineDisplayHistory } from "@/hooks/queries/use-machine-displays";

interface MachineDisplayHistorySheetProps {
  display: MachineDisplay | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function HistorySkeleton() {
  return (
    <div className="space-y-4">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="border rounded-lg p-4 space-y-2">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-3 w-24" />
        </div>
      ))}
    </div>
  );
}

function formatDuration(startedAt: string, endedAt: string | null): string {
  const start = new Date(startedAt);
  const end = endedAt ? new Date(endedAt) : new Date();
  const days = Math.floor(
    (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)
  );

  if (days === 0) return "< 1 day";
  if (days === 1) return "1 day";
  return `${days} days`;
}

export function MachineDisplayHistorySheet({
  display,
  open,
  onOpenChange,
}: MachineDisplayHistorySheetProps) {
  const { data: history, isLoading } = useMachineDisplayHistory(
    display?.locationType,
    display?.machineId
  );

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-lg">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <History className="h-5 w-5" />
            Display History
          </SheetTitle>
          {display && (
            <SheetDescription>
              History for {display.machineCode} (
              {LOCATION_TYPE_LABELS[display.locationType]})
            </SheetDescription>
          )}
        </SheetHeader>

        <ScrollArea className="h-[calc(100vh-120px)] mt-4 pr-4">
          {isLoading ? (
            <HistorySkeleton />
          ) : !history || history.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-48 text-muted-foreground">
              <History className="h-12 w-12 mb-2 opacity-50" />
              <p>No history found</p>
            </div>
          ) : (
            <div className="space-y-3">
              {history.map((item, index) => (
                <div
                  key={item.id}
                  className={`border rounded-lg p-4 space-y-2 ${
                    index === 0 && !item.endedAt
                      ? "border-primary bg-primary/5"
                      : ""
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="font-medium">{item.productName}</p>
                      <p className="text-sm text-muted-foreground">
                        {item.productSku}
                      </p>
                    </div>
                    {index === 0 && !item.endedAt ? (
                      <Badge>Current</Badge>
                    ) : (
                      <Badge variant="outline">
                        {formatDuration(item.startedAt, item.endedAt)}
                      </Badge>
                    )}
                  </div>

                  <div className="text-sm text-muted-foreground space-y-1">
                    <p>
                      Started:{" "}
                      {format(new Date(item.startedAt), "MMM d, yyyy h:mm a")}
                    </p>
                    {item.endedAt && (
                      <p>
                        Ended:{" "}
                        {format(new Date(item.endedAt), "MMM d, yyyy h:mm a")}
                      </p>
                    )}
                    {item.actorName && <p>Set by: {item.actorName}</p>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </ScrollArea>
      </SheetContent>
    </Sheet>
  );
}
