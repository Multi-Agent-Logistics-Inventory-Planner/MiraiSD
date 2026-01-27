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
import { Checkbox } from "@/components/ui/checkbox";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAuth } from "@/hooks/use-auth";
import { useReceiveShipmentMutation } from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import type { Shipment, ShipmentItemReceipt } from "@/types/api";

interface ShipmentReceiveDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  shipment: Shipment | null;
}

export function ShipmentReceiveDialog({
  open,
  onOpenChange,
  shipment,
}: ShipmentReceiveDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const receiveMutation = useReceiveShipmentMutation();

  const [deliveryDate, setDeliveryDate] = useState<string>("");
  const [receiveAll, setReceiveAll] = useState(false);
  const [quantities, setQuantities] = useState<Record<string, number>>({});

  // Reset form when dialog opens
  useEffect(() => {
    if (open && shipment) {
      const today = new Date().toISOString().split("T")[0];
      setDeliveryDate(today);
      setReceiveAll(false);

      // Initialize quantities with remaining amounts
      const initialQuantities: Record<string, number> = {};
      shipment.items.forEach((item) => {
        const remaining = item.orderedQuantity - item.receivedQuantity;
        initialQuantities[item.id] = remaining > 0 ? remaining : 0;
      });
      setQuantities(initialQuantities);
    }
  }, [open, shipment]);

  // Handle receive all toggle
  useEffect(() => {
    if (!shipment) return;
    if (receiveAll) {
      const maxQuantities: Record<string, number> = {};
      shipment.items.forEach((item) => {
        const remaining = item.orderedQuantity - item.receivedQuantity;
        maxQuantities[item.id] = remaining > 0 ? remaining : 0;
      });
      setQuantities(maxQuantities);
    }
  }, [receiveAll, shipment]);

  if (!shipment) return null;

  const handleQuantityChange = (itemId: string, value: string) => {
    const num = parseInt(value, 10);
    const item = shipment.items.find((i) => i.id === itemId);
    if (!item) return;

    const maxAllowed = item.orderedQuantity - item.receivedQuantity;
    const clamped = Number.isNaN(num) ? 0 : Math.max(0, Math.min(num, maxAllowed));

    setQuantities((prev) => ({ ...prev, [itemId]: clamped }));
    setReceiveAll(false);
  };

  const hasAnyToReceive = Object.values(quantities).some((q) => q > 0);

  async function handleSubmit() {
    if (!user?.personId || !shipment) return;

    const itemReceipts: ShipmentItemReceipt[] = shipment.items
      .filter((item) => (quantities[item.id] ?? 0) > 0)
      .map((item) => ({
        shipmentItemId: item.id,
        receivedQuantity: quantities[item.id] ?? 0,
      }));

    if (itemReceipts.length === 0) {
      toast({
        title: "No items to receive",
        description: "Please enter quantities for at least one item.",
      });
      return;
    }

    try {
      await receiveMutation.mutateAsync({
        id: shipment.id,
        payload: {
          actualDeliveryDate: deliveryDate,
          receivedBy: user.personId,
          itemReceipts,
        },
      });
      toast({ title: "Items received successfully" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to receive items";
      toast({ title: "Error", description: message });
    }
  }

  const isSaving = receiveMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Receive Items</DialogTitle>
          <DialogDescription>
            Record received quantities for shipment {shipment.shipmentNumber}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="deliveryDate">Actual Delivery Date</Label>
            <Input
              id="deliveryDate"
              type="date"
              value={deliveryDate}
              onChange={(e) => setDeliveryDate(e.target.value)}
            />
          </div>

          <div className="flex items-center space-x-2">
            <Checkbox
              id="receiveAll"
              checked={receiveAll}
              onCheckedChange={(checked) => setReceiveAll(checked === true)}
            />
            <Label htmlFor="receiveAll" className="text-sm font-normal">
              Receive all remaining items
            </Label>
          </div>

          <div className="rounded-lg border overflow-hidden">
            <Table>
              <TableHeader className="bg-muted">
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead className="text-right">Ordered</TableHead>
                  <TableHead className="text-right">Already Received</TableHead>
                  <TableHead className="text-right">Receiving Now</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {shipment.items.map((item) => {
                  const remaining = item.orderedQuantity - item.receivedQuantity;
                  const isComplete = remaining <= 0;

                  return (
                    <TableRow
                      key={item.id}
                      className={isComplete ? "opacity-50" : ""}
                    >
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="font-medium">{item.item.name}</span>
                          <span className="text-xs text-muted-foreground font-mono">
                            {item.item.sku}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell className="text-right">
                        {item.orderedQuantity}
                      </TableCell>
                      <TableCell className="text-right">
                        {item.receivedQuantity}
                      </TableCell>
                      <TableCell className="text-right">
                        <Input
                          type="number"
                          min={0}
                          max={remaining}
                          value={quantities[item.id] ?? 0}
                          onChange={(e) =>
                            handleQuantityChange(item.id, e.target.value)
                          }
                          disabled={isComplete}
                          className="w-20 ml-auto text-right"
                        />
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
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
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={isSaving || !hasAnyToReceive}
          >
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Receive Items
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
