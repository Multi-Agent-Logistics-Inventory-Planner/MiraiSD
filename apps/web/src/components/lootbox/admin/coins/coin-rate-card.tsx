"use client";

import { useState } from "react";
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
import { toast } from "@/hooks/use-toast";
import { useCoinEconomyConfig } from "@/hooks/queries/use-lootbox";
import { useUpdateCoinEconomyConfigMutation } from "@/hooks/mutations/use-lootbox-mutations";

function formatChangedBy(name: string | null, updatedAt: string): string {
  const when = new Date(updatedAt).toLocaleString();
  if (!name) return `system, ${when}`;
  return `${name}, ${when}`;
}

/**
 * Review-to-coin rate editor. Moved out of the standalone /team/lootbox-admin/coin-config
 * page into the unified admin modal so all coin economy controls live in one place.
 */
export function CoinRateCard() {
  const configQuery = useCoinEconomyConfig();
  const [editOpen, setEditOpen] = useState(false);
  const config = configQuery.data;

  return (
    <section className="rounded-xl border border-border bg-card/40 p-5">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex flex-col gap-0.5">
          <h3 className="text-[14px] font-medium text-foreground">Review coin rate</h3>
          <p className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
            Coins per 5-star review with an employee mention
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
        <div className="space-y-2">
          <div className="flex items-baseline gap-2">
            <span className="font-mono text-3xl font-semibold tabular-nums text-foreground">
              {config.reviewCoinRate}
            </span>
            <span className="text-sm text-muted-foreground">
              coin{config.reviewCoinRate === 1 ? "" : "s"} per review
            </span>
          </div>
          <div className="space-y-1 text-xs text-muted-foreground">
            <div>
              Last changed:{" "}
              <span className="font-medium text-foreground">
                {formatChangedBy(config.updatedByName, config.updatedAt)}
              </span>
            </div>
            <p>{config.nextFetchHint}</p>
          </div>
        </div>
      ) : (
        <p className="text-sm text-rose-500">Failed to load coin economy config.</p>
      )}

      <EditRateDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        currentRate={config?.reviewCoinRate ?? null}
        hint={config?.nextFetchHint ?? null}
      />
    </section>
  );
}

interface EditRateDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly currentRate: number | null;
  readonly hint: string | null;
}

function EditRateDialog({ open, onOpenChange, currentRate, hint }: EditRateDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit review coin rate</DialogTitle>
        </DialogHeader>
        {open ? (
          <EditRateDialogBody
            key={String(currentRate ?? "none")}
            currentRate={currentRate}
            hint={hint}
            onClose={() => onOpenChange(false)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function EditRateDialogBody({
  currentRate,
  hint,
  onClose,
}: {
  readonly currentRate: number | null;
  readonly hint: string | null;
  readonly onClose: () => void;
}) {
  const updateMut = useUpdateCoinEconomyConfigMutation();
  const [rate, setRate] = useState<string>(
    currentRate !== null ? String(currentRate) : ""
  );

  const parsed = Number.parseInt(rate, 10);
  const valid = Number.isFinite(parsed) && parsed >= 0;
  const changed = currentRate !== null && parsed !== currentRate;
  const canSubmit = valid && changed && !updateMut.isPending;

  const submit = async () => {
    if (!canSubmit) return;
    try {
      await updateMut.mutateAsync({ reviewCoinRate: parsed });
      toast({ title: `Review coin rate set to ${parsed}.`, variant: "success" });
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update rate.";
      toast({ title: "Couldn't update rate", description: message });
    }
  };

  return (
    <>
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
            <p className="text-xs text-rose-500">Rate must be a non-negative integer.</p>
          ) : null}
        </div>
        {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
      </div>
      <DialogFooter>
        <Button variant="ghost" onClick={onClose} disabled={updateMut.isPending}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!canSubmit}>
          {updateMut.isPending ? "Saving…" : "Save rate"}
        </Button>
      </DialogFooter>
    </>
  );
}
