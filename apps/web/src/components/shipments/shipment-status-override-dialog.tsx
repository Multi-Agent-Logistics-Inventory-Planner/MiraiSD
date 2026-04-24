"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { useOverrideShipmentStatusMutation } from "@/hooks/mutations/use-shipment-mutations";
import {
  ShipmentStatus,
  SHIPMENT_STATUS_LABELS,
  type Shipment,
} from "@/types/api";
import { calculateTotalReceived } from "@/lib/shipment-utils";

interface ShipmentStatusOverrideDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  shipment: Shipment | null;
  onSuccess?: (updated: Shipment) => void;
}

const OVERRIDE_TARGETS: ShipmentStatus[] = [
  ShipmentStatus.PENDING,
  ShipmentStatus.RECEIVED,
];

export function ShipmentStatusOverrideDialog({
  open,
  onOpenChange,
  shipment,
  onSuccess,
}: ShipmentStatusOverrideDialogProps) {
  const { toast } = useToast();
  const overrideMutation = useOverrideShipmentStatusMutation();
  const [target, setTarget] = useState<ShipmentStatus | "">("");
  const [reason, setReason] = useState("");

  function handleOpenChange(next: boolean) {
    if (!next) {
      setTarget("");
      setReason("");
    }
    onOpenChange(next);
  }

  if (!shipment) return null;

  const totalReceived = calculateTotalReceived(shipment.items);
  const totalOrdered = shipment.items.reduce(
    (sum, item) => sum + item.orderedQuantity,
    0,
  );
  const mathSatisfiesReceived = totalOrdered > 0 && totalReceived >= totalOrdered;

  // Warn if user is about to set a state that doesn't match item math
  let mismatchWarning: string | null = null;
  if (target === ShipmentStatus.RECEIVED && !mathSatisfiesReceived) {
    mismatchWarning =
      "This shipment has fewer items received than ordered. Marking it Received will not change inventory - it's a label change only.";
  }
  if (target === ShipmentStatus.PENDING && mathSatisfiesReceived) {
    mismatchWarning =
      "All items are already accounted for. Marking this Pending only changes the label - inventory remains.";
  }

  const canSubmit =
    target !== "" && target !== shipment.status && reason.trim().length > 0 && !overrideMutation.isPending;

  async function handleSubmit() {
    if (!shipment || target === "" || target === shipment.status) return;
    try {
      const updated = await overrideMutation.mutateAsync({
        id: shipment.id,
        payload: { status: target, reason: reason.trim() },
      });
      toast({
        title: "Status updated",
        description: `Shipment ${shipment.shipmentNumber} is now ${SHIPMENT_STATUS_LABELS[target]}.`,
      });
      onSuccess?.(updated);
      handleOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Override failed";
      toast({
        title: "Could not override status",
        description: message,
        variant: "destructive",
      });
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Override Shipment Status</DialogTitle>
          <DialogDescription>
            Manually change the inventory status of shipment{" "}
            <span className="font-mono">{shipment.shipmentNumber}</span>. This is a
            label change only - it doesn&apos;t add or remove inventory.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="text-sm">
            <span className="text-muted-foreground">Current status: </span>
            <span className="font-medium">
              {SHIPMENT_STATUS_LABELS[shipment.status] ?? shipment.status}
            </span>
          </div>

          <div className="space-y-2">
            <Label htmlFor="override-target">New status</Label>
            <Select
              value={target}
              onValueChange={(v) => setTarget(v as ShipmentStatus)}
            >
              <SelectTrigger id="override-target">
                <SelectValue placeholder="Select new status" />
              </SelectTrigger>
              <SelectContent>
                {OVERRIDE_TARGETS.filter((s) => s !== shipment.status).map(
                  (s) => (
                    <SelectItem key={s} value={s}>
                      Mark as {SHIPMENT_STATUS_LABELS[s]}
                    </SelectItem>
                  ),
                )}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="override-reason">
              Reason <span className="text-destructive">*</span>
            </Label>
            <textarea
              id="override-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              placeholder="Why are you overriding the status? (recorded in the audit log)"
              className={cn(
                "placeholder:text-muted-foreground dark:bg-input dark:border-[#41413d]",
                "w-full rounded-md border bg-transparent px-3 py-2 text-sm shadow-xs",
                "outline-none transition-[color,box-shadow]",
                "focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]",
              )}
            />
          </div>

          {mismatchWarning && (
            <div className="text-sm text-amber-700 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 p-3 rounded-md">
              {mismatchWarning}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={overrideMutation.isPending}
          >
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={!canSubmit}>
            {overrideMutation.isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Saving...
              </>
            ) : (
              "Save override"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
