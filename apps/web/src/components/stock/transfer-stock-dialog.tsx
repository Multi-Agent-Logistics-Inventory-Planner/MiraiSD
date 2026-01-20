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
import { useTransferStockMutation } from "@/hooks/mutations/use-stock-mutations";
import type { Product } from "@/types/api";

const schema = z
  .object({
    sourceInventoryId: z.string().min(1, "Select a source location"),
    destinationInventoryId: z.string().min(1, "Select a destination location"),
    quantity: z.coerce.number().int().min(1, "Quantity must be at least 1"),
    notes: z.string().optional(),
  })
  .refine((values) => values.sourceInventoryId !== values.destinationInventoryId, {
    message: "Source and destination must differ",
    path: ["destinationInventoryId"],
  });

type FormValues = z.infer<typeof schema>;

interface TransferStockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product?: Product | null;
}

export function TransferStockDialog({
  open,
  onOpenChange,
  product,
}: TransferStockDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const inventoryQuery = useInventoryByItemId(product?.id);
  const transferMutation = useTransferStockMutation();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      sourceInventoryId: "",
      destinationInventoryId: "",
      quantity: 1,
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

    const [first, second] = inventoryQuery.data;
    if (!form.getValues("sourceInventoryId") && first) {
      form.setValue("sourceInventoryId", first.inventoryId);
    }
    if (!form.getValues("destinationInventoryId") && second) {
      form.setValue("destinationInventoryId", second.inventoryId);
    }
  }, [open, inventoryQuery.data, form]);

  const sourceId = form.watch("sourceInventoryId");
  const destinationId = form.watch("destinationInventoryId");
  const quantity = form.watch("quantity") ?? 0;

  const sourceEntry = useMemo(() => {
    return inventoryQuery.data?.find((entry) => entry.inventoryId === sourceId);
  }, [inventoryQuery.data, sourceId]);

  const destinationEntry = useMemo(() => {
    return inventoryQuery.data?.find(
      (entry) => entry.inventoryId === destinationId
    );
  }, [inventoryQuery.data, destinationId]);

  const destinationOptions = useMemo(() => {
    return (inventoryQuery.data ?? []).filter(
      (entry) => entry.inventoryId !== sourceId
    );
  }, [inventoryQuery.data, sourceId]);

  useEffect(() => {
    if (!sourceId || !destinationId) return;
    if (sourceId !== destinationId) return;
    const fallback = destinationOptions[0];
    if (fallback) {
      form.setValue("destinationInventoryId", fallback.inventoryId);
    }
  }, [sourceId, destinationId, destinationOptions, form]);

  const isSaving = transferMutation.isPending;
  const isInventoryLoading = inventoryQuery.isLoading;
  const hasInventory = !isInventoryLoading && (inventoryQuery.data?.length ?? 0) > 1;

  const sourceQty = sourceEntry?.quantity ?? 0;
  const destinationQty = destinationEntry?.quantity ?? 0;
  const previewSourceQty = sourceQty - quantity;
  const previewDestinationQty = destinationQty + quantity;

  async function onSubmit(values: FormValues) {
    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }

    if (!sourceEntry || !destinationEntry) {
      toast({
        title: "Missing locations",
        description: "Select both source and destination locations.",
      });
      return;
    }

    if (values.quantity > sourceEntry.quantity) {
      toast({
        title: "Quantity too high",
        description: "Transfer quantity exceeds source stock.",
      });
      return;
    }

    try {
      await transferMutation.mutateAsync({
        payload: {
          sourceLocationType: sourceEntry.locationType,
          sourceInventoryId: sourceEntry.inventoryId,
          destinationLocationType: destinationEntry.locationType,
          destinationInventoryId: destinationEntry.inventoryId,
          quantity: values.quantity,
          actorId,
          notes: values.notes?.trim() || undefined,
        },
        productId: product?.id,
      });
      toast({ title: "Stock transferred" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Transfer failed";
      toast({ title: "Transfer failed", description: message });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Transfer Stock</DialogTitle>
          <DialogDescription>
            {product
              ? `Move inventory for ${product.name} between locations.`
              : "Select a product to transfer stock."}
          </DialogDescription>
        </DialogHeader>

        {!product ? (
          <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">
            Select a product before transferring stock.
          </div>
        ) : (
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid gap-2">
              <Label>Source Location</Label>
              <Select
                value={sourceId}
                onValueChange={(v) => form.setValue("sourceInventoryId", v)}
                disabled={!hasInventory}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select source" />
                </SelectTrigger>
                <SelectContent>
                  {(inventoryQuery.data ?? []).map((entry) => (
                    <SelectItem key={entry.inventoryId} value={entry.inventoryId}>
                      {entry.locationLabel} (qty {entry.quantity})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.formState.errors.sourceInventoryId?.message ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.sourceInventoryId.message}
                </p>
              ) : null}
              {isInventoryLoading ? (
                <p className="text-xs text-muted-foreground">Loading inventory...</p>
              ) : null}
            </div>

            <div className="grid gap-2">
              <Label>Destination Location</Label>
              <Select
                value={destinationId}
                onValueChange={(v) =>
                  form.setValue("destinationInventoryId", v)
                }
                disabled={!hasInventory}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select destination" />
                </SelectTrigger>
                <SelectContent>
                  {destinationOptions.map((entry) => (
                    <SelectItem key={entry.inventoryId} value={entry.inventoryId}>
                      {entry.locationLabel} (qty {entry.quantity})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.formState.errors.destinationInventoryId?.message ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.destinationInventoryId.message}
                </p>
              ) : null}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="quantity">Quantity to Transfer</Label>
              <Input
                id="quantity"
                type="number"
                step="1"
                min={1}
                max={sourceEntry?.quantity ?? undefined}
                {...form.register("quantity")}
              />
              {form.formState.errors.quantity?.message ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.quantity.message}
                </p>
              ) : null}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="notes">Notes (optional)</Label>
              <Textarea id="notes" rows={3} {...form.register("notes")} />
            </div>

            <div className="rounded-md border p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Source after transfer</span>
                <span className="font-medium">{previewSourceQty}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Destination after transfer</span>
                <span className="font-medium">{previewDestinationQty}</span>
              </div>
            </div>

            {!hasInventory && !isInventoryLoading ? (
              <div className="rounded-md border border-dashed p-3 text-sm text-muted-foreground">
                Add inventory at another location before transferring.
              </div>
            ) : null}

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
                Transfer Stock
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
