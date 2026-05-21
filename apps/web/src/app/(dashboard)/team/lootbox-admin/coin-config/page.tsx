"use client";

import { useEffect, useState } from "react";
import { Pencil } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { toast } from "@/hooks/use-toast";
import { useCoinEconomyConfig } from "@/hooks/queries/use-lootbox";
import { useUpdateCoinEconomyConfigMutation } from "@/hooks/mutations/use-lootbox-mutations";

/**
 * Admin panel for editing the review-to-coin rate. The rate is captured per row
 * at the next 6 AM ET review fetch (the messaging-service snapshots it at batch
 * start), so existing balances stay frozen and changes only affect future activity.
 */
export default function CoinConfigPage() {
  const configQuery = useCoinEconomyConfig();
  const [editOpen, setEditOpen] = useState(false);

  const config = configQuery.data;

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger />
        <h1 className="text-2xl font-semibold tracking-tight">Coin economy</h1>
      </div>

      <div className="rounded-md border p-4 md:p-6 space-y-4 max-w-xl">
        <div className="flex items-center justify-between gap-2">
          <div>
            <h2 className="text-base font-medium">Review coin rate</h2>
            <p className="text-xs text-muted-foreground">
              Coins granted per 5-star review with an employee mention.
            </p>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={() => setEditOpen(true)}
            disabled={configQuery.isLoading || !config}
          >
            <Pencil className="h-4 w-4" />
            Edit
          </Button>
        </div>

        {configQuery.isLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : config ? (
          <>
            <div className="flex items-baseline gap-2">
              <span className="text-4xl font-semibold tabular-nums">
                {config.reviewCoinRate}
              </span>
              <span className="text-sm text-muted-foreground">
                coin{config.reviewCoinRate === 1 ? "" : "s"} per review
              </span>
            </div>

            <div className="text-xs text-muted-foreground space-y-1">
              <div>
                Last changed:{" "}
                <span className="font-medium text-foreground">
                  {formatChangedBy(config.updatedByName, config.updatedAt)}
                </span>
              </div>
              <p>{config.nextFetchHint}</p>
            </div>
          </>
        ) : (
          <p className="text-sm text-rose-600">Failed to load coin economy config.</p>
        )}
      </div>

      <EditRateDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        currentRate={config?.reviewCoinRate ?? null}
        hint={config?.nextFetchHint ?? null}
      />
    </div>
  );
}

function formatChangedBy(name: string | null, updatedAt: string): string {
  const when = new Date(updatedAt).toLocaleString();
  if (!name) return `system, ${when}`;
  return `${name}, ${when}`;
}

interface EditRateDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly currentRate: number | null;
  readonly hint: string | null;
}

function EditRateDialog({ open, onOpenChange, currentRate, hint }: EditRateDialogProps) {
  const updateMut = useUpdateCoinEconomyConfigMutation();
  const [rate, setRate] = useState<string>("");

  // Reset the input each time the dialog opens to mirror the latest server value.
  useEffect(() => {
    if (open && currentRate !== null) {
      setRate(String(currentRate));
    }
  }, [open, currentRate]);

  const parsed = Number.parseInt(rate, 10);
  const valid = Number.isFinite(parsed) && parsed >= 0;
  const changed = currentRate !== null && parsed !== currentRate;
  const canSubmit = valid && changed && !updateMut.isPending;

  const submit = async () => {
    if (!canSubmit) return;
    try {
      await updateMut.mutateAsync({ reviewCoinRate: parsed });
      toast({ title: `Review coin rate set to ${parsed}.`, variant: "success" });
      onOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update rate.";
      toast({ title: "Couldn't update rate", description: message });
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit review coin rate</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="review-coin-rate">Coins per 5-star review</Label>
            <Input
              id="review-coin-rate"
              type="number"
              min="0"
              step="1"
              value={rate}
              onChange={(e) => setRate(e.target.value)}
            />
            {!valid ? (
              <p className="text-xs text-rose-600">Rate must be a non-negative integer.</p>
            ) : null}
          </div>
          {hint ? (
            <p className="text-xs text-muted-foreground">{hint}</p>
          ) : null}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={updateMut.isPending}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!canSubmit}>
            {updateMut.isPending ? "Saving…" : "Save rate"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
