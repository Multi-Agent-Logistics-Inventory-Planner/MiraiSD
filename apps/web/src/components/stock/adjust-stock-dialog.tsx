"use client";

import { useEffect, useMemo } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useInventoryByItemId } from "@/hooks/queries/use-inventory-by-item";
import { useAdjustStockMutation } from "@/hooks/mutations/use-stock-mutations";
import { StockMovementReason, type Product } from "@/types/api";

const ADJUST_REASONS = [
  StockMovementReason.SALE,
  StockMovementReason.DAMAGE,
  StockMovementReason.ADJUSTMENT,
  StockMovementReason.RETURN,
  StockMovementReason.RESTOCK,
] as const;

const schema = z.object({
  inventoryId: z.string().min(1, "Select a location"),
  quantityChange: z.coerce.number().int().refine((v) => v !== 0, {
    message: "Adjustment must be non-zero",
  }),
  reason: z.enum(ADJUST_REASONS),
  notes: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

interface AdjustStockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product?: Product | null;
}

export function AdjustStockDialog({
  open,
  onOpenChange,
  product,
}: AdjustStockDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const inventoryQuery = useInventoryByItemId(product?.id);
  const adjustMutation = useAdjustStockMutation();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      inventoryId: "",
      quantityChange: 0,
      reason: StockMovementReason.ADJUSTMENT,
      notes: "",
    },
  });

  useEffect(() => {
    if (!open) {
      form.reset();
    }
  }, [open, form]);

  useEffect(() => {
    if (!open) return;
    if (!inventoryQuery.data || inventoryQuery.data.length === 0) return;
    if (form.getValues("inventoryId")) return;
    form.setValue("inventoryId", inventoryQuery.data[0].inventoryId);
  }, [open, inventoryQuery.data, form]);

  const selectedInventoryId = form.watch("inventoryId");
  const quantityChange = form.watch("quantityChange") ?? 0;

  const selectedInventory = useMemo(() => {
    return inventoryQuery.data?.find(
      (entry) => entry.inventoryId === selectedInventoryId
    );
  }, [inventoryQuery.data, selectedInventoryId]);

  const currentQty = selectedInventory?.quantity ?? 0;
  const previewQty = currentQty + quantityChange;

  const isSaving = adjustMutation.isPending;
  const isInventoryLoading = inventoryQuery.isLoading;
  const hasInventory =
    !isInventoryLoading && (inventoryQuery.data?.length ?? 0) > 0 && Boolean(product?.id);

  async function onSubmit(values: FormValues) {
    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }

    if (!selectedInventory) {
      toast({
        title: "Missing location",
        description: "Select a location before adjusting stock.",
      });
      return;
    }

    try {
      await adjustMutation.mutateAsync({
        locationType: selectedInventory.locationType,
        inventoryId: selectedInventory.inventoryId,
        payload: {
          quantityChange: values.quantityChange,
          reason: values.reason,
          actorId,
          notes: values.notes?.trim() || undefined,
        },
        productId: product?.id,
      });
      toast({ title: "Stock adjusted" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Adjustment failed";
      toast({ title: "Adjustment failed", description: message });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Adjust Stock</DialogTitle>
          <DialogDescription>
            {product
              ? `Update inventory for ${product.name}.`
              : "Select a product to adjust stock."}
          </DialogDescription>
        </DialogHeader>

        {!product ? (
          <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">
            Select a product before adjusting stock.
          </div>
        ) : (
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid gap-2">
              <Label>Location</Label>
              <Select
                value={selectedInventoryId}
                onValueChange={(v) => form.setValue("inventoryId", v)}
                disabled={!hasInventory}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select location" />
                </SelectTrigger>
                <SelectContent>
                  {(inventoryQuery.data ?? []).map((entry) => (
                    <SelectItem key={entry.inventoryId} value={entry.inventoryId}>
                      {entry.locationLabel} (qty {entry.quantity})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.formState.errors.inventoryId?.message ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.inventoryId.message}
                </p>
              ) : null}
              {isInventoryLoading ? (
                <p className="text-xs text-muted-foreground">Loading inventory...</p>
              ) : null}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="quantityChange">Adjustment (+ / -)</Label>
              <Input
                id="quantityChange"
                type="number"
                step="1"
                {...form.register("quantityChange")}
              />
              {form.formState.errors.quantityChange?.message ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.quantityChange.message}
                </p>
              ) : null}
            </div>

            <div className="grid gap-2">
              <Label>Reason</Label>
              <Select
                value={form.watch("reason")}
                onValueChange={(v) =>
                  form.setValue("reason", v as FormValues["reason"])
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select reason" />
                </SelectTrigger>
                <SelectContent>
                  {ADJUST_REASONS.map((reason) => (
                    <SelectItem key={reason} value={reason}>
                      {reason}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="notes">Notes (optional)</Label>
              <Textarea id="notes" rows={3} {...form.register("notes")} />
            </div>

            <div className="rounded-md border p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Current quantity</span>
                <span className="font-medium">{currentQty}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">New quantity</span>
                <span className="font-semibold">{previewQty}</span>
              </div>
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={!hasInventory || isSaving}>
                {isSaving ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : null}
                Adjust Stock
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
