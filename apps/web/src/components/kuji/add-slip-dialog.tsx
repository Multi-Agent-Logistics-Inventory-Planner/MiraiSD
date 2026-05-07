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
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useAddKujiSlipMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { KujiBox, KujiBoxTier } from "@/types/api";

interface AddSlipDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
  readonly tier: KujiBoxTier;
}

export function AddSlipDialog({
  open,
  onOpenChange,
  box,
  tier,
}: AddSlipDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const addSlip = useAddKujiSlipMutation();

  const [quantity, setQuantity] = useState<number | "">(1);
  const [error, setError] = useState("");

  useEffect(() => {
    if (open) {
      setQuantity(1);
      setError("");
    }
  }, [open]);

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    if (quantity === "" || quantity < 1) {
      setError("Quantity must be at least 1");
      return;
    }

    try {
      await addSlip.mutateAsync({
        boxId: box.id,
        tierId: tier.id,
        productId: box.productId,
        payload: { actorId, quantity },
      });
      toast({ title: "Slip(s) added", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to add slip";
      toast({ title: "Add slip failed", description: message });
    }
  }

  const isPending = addSlip.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Add Slip</DialogTitle>
          <DialogDescription>
            Add additional draw slips to{" "}
            <span className="font-medium text-foreground">{tier.label}</span>
            {tier.letter ? ` (${tier.letter})` : null}. This does not change
            linked-product inventory.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-2">
          <Label htmlFor="add-slip-qty">Quantity</Label>
          <Input
            id="add-slip-qty"
            type="number"
            min={1}
            value={quantity}
            onChange={(e) => {
              const raw = e.target.value;
              if (raw === "") {
                setQuantity("");
                return;
              }
              const v = parseInt(raw, 10);
              if (!Number.isNaN(v) && v >= 1) {
                setQuantity(v);
                setError("");
              }
            }}
            disabled={isPending}
          />
          {error ? <p className="text-xs text-destructive">{error}</p> : null}
        </div>

        <DialogFooter>
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
            onClick={handleSubmit}
            disabled={isPending || quantity === "" || quantity < 1}
          >
            {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Add Slip
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
