"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useUndoKujiDrawMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import { useAuditLogs } from "@/hooks/queries/use-audit-log";
import { StockMovementReason, type KujiBox } from "@/types/api";

interface UndoDrawDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
}

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function UndoDrawDialog({
  open,
  onOpenChange,
  box,
}: UndoDrawDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const undoDraw = useUndoKujiDrawMutation();

  const drawsQuery = useAuditLogs(
    {
      productId: box.productId,
      reason: StockMovementReason.KUJI_PRIZE_WON,
      fromDate: box.openedAt.slice(0, 10),
    },
    0,
    100,
  );

  const [selectedAuditLogId, setSelectedAuditLogId] = useState<string | null>(
    null,
  );
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    if (!open) {
      setSelectedAuditLogId(null);
      setConfirmOpen(false);
    }
  }, [open]);

  const draws = (drawsQuery.data?.content ?? []).filter((log) => !log.reversed);
  const selectedDraw = draws.find((d) => d.id === selectedAuditLogId);

  async function handleConfirm() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId || !selectedAuditLogId) return;

    try {
      await undoDraw.mutateAsync({
        boxId: box.id,
        auditLogId: selectedAuditLogId,
        actorId,
        productId: box.productId,
      });
      toast({ title: "Draw undone", variant: "success" });
      setConfirmOpen(false);
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to undo draw";
      toast({ title: "Undo failed", description: message });
      setConfirmOpen(false);
    }
  }

  const isPending = undoDraw.isPending;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="w-[calc(100%-2rem)] sm:max-w-lg max-h-[80vh] flex flex-col gap-0 p-0 overflow-hidden">
          <DialogHeader className="p-6 pb-2">
            <DialogTitle>Undo Draw</DialogTitle>
            <DialogDescription>
              Pick a draw to reverse. Slips will be returned to the tier and any
              linked-product inventory restored.
            </DialogDescription>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto px-6 pb-4">
            {drawsQuery.isLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
              </div>
            ) : draws.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">
                No draws to undo for this box.
              </div>
            ) : (
              <ul className="divide-y rounded-md border">
                {draws.map((log) => {
                  const isSelected = log.id === selectedAuditLogId;
                  return (
                    <li key={log.id}>
                      <button
                        type="button"
                        onClick={() => setSelectedAuditLogId(log.id)}
                        disabled={isPending}
                        className={`flex w-full items-start gap-3 px-3 py-2.5 text-left transition-colors hover:bg-muted ${
                          isSelected ? "bg-muted" : ""
                        }`}
                        aria-pressed={isSelected}
                      >
                        <span
                          className={`mt-1.5 inline-block h-3 w-3 shrink-0 rounded-full border-2 ${
                            isSelected
                              ? "border-primary bg-primary"
                              : "border-muted-foreground/40"
                          }`}
                          aria-hidden
                        />
                        <div className="min-w-0 flex-1">
                          <div className="text-sm">
                            {log.totalQuantityMoved}{" "}
                            {log.totalQuantityMoved === 1 ? "slip" : "slips"}{" "}
                            drawn
                            {log.actorName ? (
                              <span className="text-muted-foreground">
                                {" "}
                                · {log.actorName}
                              </span>
                            ) : null}
                          </div>
                          {log.productSummary ? (
                            <div className="mt-0.5 truncate text-xs text-muted-foreground">
                              {log.productSummary.replace(/\n/g, ", ")}
                            </div>
                          ) : null}
                          {log.notes ? (
                            <div className="mt-0.5 truncate text-[11px] italic text-muted-foreground">
                              {log.notes}
                            </div>
                          ) : null}
                        </div>
                        <span className="shrink-0 pt-0.5 text-xs text-muted-foreground tabular-nums">
                          {formatDateTime(log.createdAt)}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          <DialogFooter className="px-6 py-4 border-t">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={() => setConfirmOpen(true)}
              disabled={isPending || !selectedAuditLogId}
            >
              Undo Draw
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Reverse this draw?</AlertDialogTitle>
            <AlertDialogDescription>
              {selectedDraw ? (
                <>
                  Slips ({selectedDraw.totalQuantityMoved}) will be returned to
                  the tier and any linked-product inventory restored. This
                  action cannot be undone again.
                </>
              ) : (
                <>
                  Slips will be returned to the tier and any linked-product
                  inventory restored.
                </>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleConfirm} disabled={isPending}>
              {isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Reverse Draw
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
