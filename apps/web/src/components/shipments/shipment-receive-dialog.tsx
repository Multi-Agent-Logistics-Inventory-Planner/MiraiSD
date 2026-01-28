"use client";

import { useEffect, useState, useMemo } from "react";
import { Loader2, Plus, Trash2 } from "lucide-react";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAuth } from "@/hooks/use-auth";
import { useReceiveShipmentMutation } from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import { useLocations } from "@/hooks/queries/use-locations";
import type {
  Shipment,
  ShipmentItemReceipt,
  DestinationAllocation,
  StorageLocation,
  BoxBin,
  Rack,
  Cabinet,
  SingleClawMachine,
  DoubleClawMachine,
  KeychainMachine,
} from "@/types/api";
import { LocationType } from "@/types/api";

function getLocationCode(location: StorageLocation): string {
  if ("boxBinCode" in location) return (location as BoxBin).boxBinCode;
  if ("rackCode" in location) return (location as Rack).rackCode;
  if ("cabinetCode" in location) return (location as Cabinet).cabinetCode;
  if ("singleClawMachineCode" in location)
    return (location as SingleClawMachine).singleClawMachineCode;
  if ("doubleClawMachineCode" in location)
    return (location as DoubleClawMachine).doubleClawMachineCode;
  if ("keychainMachineCode" in location)
    return (location as KeychainMachine).keychainMachineCode;
  return "";
}

const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  [LocationType.NOT_ASSIGNED]: "Not Assigned",
  [LocationType.BOX_BIN]: "Box/Bin",
  [LocationType.RACK]: "Rack",
  [LocationType.CABINET]: "Cabinet",
  [LocationType.SINGLE_CLAW_MACHINE]: "Single Claw",
  [LocationType.DOUBLE_CLAW_MACHINE]: "Double Claw",
  [LocationType.KEYCHAIN_MACHINE]: "Keychain",
  [LocationType.FOUR_CORNER_MACHINE]: "Four Corner",
  [LocationType.PUSHER_MACHINE]: "Pusher",
};

interface ItemAllocation {
  id: string;
  locationType: LocationType;
  locationId: string;
  quantity: number;
}

interface ItemAllocations {
  [itemId: string]: ItemAllocation[];
}

interface ShipmentReceiveDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  shipment: Shipment | null;
}

function AllocationRow({
  allocation,
  maxQuantity,
  onUpdate,
  onRemove,
  canRemove,
}: {
  allocation: ItemAllocation;
  maxQuantity: number;
  onUpdate: (updates: Partial<ItemAllocation>) => void;
  onRemove: () => void;
  canRemove: boolean;
}) {
  const locationsQuery = useLocations(
    allocation.locationType === LocationType.NOT_ASSIGNED
      ? LocationType.BOX_BIN
      : allocation.locationType
  );

  const locations = useMemo(() => {
    return (locationsQuery.data ?? []) as StorageLocation[];
  }, [locationsQuery.data]);

  const showLocationSelect =
    allocation.locationType !== LocationType.NOT_ASSIGNED;

  return (
    <div className="flex items-center gap-2 py-1">
      <Select
        value={allocation.locationType}
        onValueChange={(value) =>
          onUpdate({ locationType: value as LocationType, locationId: "" })
        }
      >
        <SelectTrigger className="w-32 h-8 text-xs">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {Object.entries(LOCATION_TYPE_LABELS).map(([value, label]) => (
            <SelectItem key={value} value={value} className="text-xs">
              {label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {showLocationSelect && (
        <Select
          value={allocation.locationId}
          onValueChange={(value) => onUpdate({ locationId: value })}
          disabled={locationsQuery.isLoading}
        >
          <SelectTrigger className="w-28 h-8 text-xs">
            <SelectValue placeholder="Location" />
          </SelectTrigger>
          <SelectContent>
            {locations.map((loc) => (
              <SelectItem key={loc.id} value={loc.id} className="text-xs">
                {getLocationCode(loc)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}

      <Input
        type="number"
        min={0}
        max={maxQuantity}
        value={allocation.quantity}
        onChange={(e) => {
          const num = parseInt(e.target.value, 10);
          onUpdate({ quantity: Number.isNaN(num) ? 0 : Math.max(0, num) });
        }}
        className="w-20 h-8 text-xs text-right"
      />

      {canRemove && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
          onClick={onRemove}
        >
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
      )}
    </div>
  );
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
  const [itemAllocations, setItemAllocations] = useState<ItemAllocations>({});

  // Reset form when dialog opens
  useEffect(() => {
    if (open && shipment) {
      const today = new Date().toISOString().split("T")[0];
      setDeliveryDate(today);

      // Initialize each item with one default allocation
      const initialAllocations: ItemAllocations = {};
      shipment.items.forEach((item) => {
        const remaining = item.orderedQuantity - item.receivedQuantity;
        initialAllocations[item.id] = [
          {
            id: crypto.randomUUID(),
            locationType: LocationType.NOT_ASSIGNED,
            locationId: "",
            quantity: remaining > 0 ? remaining : 0,
          },
        ];
      });
      setItemAllocations(initialAllocations);
    }
  }, [open, shipment]);

  if (!shipment) return null;

  const addAllocation = (itemId: string) => {
    setItemAllocations((prev) => ({
      ...prev,
      [itemId]: [
        ...(prev[itemId] || []),
        {
          id: crypto.randomUUID(),
          locationType: LocationType.NOT_ASSIGNED,
          locationId: "",
          quantity: 0,
        },
      ],
    }));
  };

  const updateAllocation = (
    itemId: string,
    allocationId: string,
    updates: Partial<ItemAllocation>
  ) => {
    setItemAllocations((prev) => ({
      ...prev,
      [itemId]: (prev[itemId] || []).map((a) =>
        a.id === allocationId ? { ...a, ...updates } : a
      ),
    }));
  };

  const removeAllocation = (itemId: string, allocationId: string) => {
    setItemAllocations((prev) => ({
      ...prev,
      [itemId]: (prev[itemId] || []).filter((a) => a.id !== allocationId),
    }));
  };

  const getTotalAllocated = (itemId: string) => {
    return (itemAllocations[itemId] || []).reduce(
      (sum, a) => sum + a.quantity,
      0
    );
  };

  const hasAnyToReceive = shipment.items.some(
    (item) => getTotalAllocated(item.id) > 0
  );

  const hasValidationErrors = shipment.items.some((item) => {
    const remaining = item.orderedQuantity - item.receivedQuantity;
    const allocated = getTotalAllocated(item.id);
    return allocated > remaining;
  });

  async function handleSubmit() {
    if (!user?.personId || !shipment) return;

    const itemReceipts: ShipmentItemReceipt[] = shipment.items
      .filter((item) => getTotalAllocated(item.id) > 0)
      .map((item) => {
        const allocations: DestinationAllocation[] = (
          itemAllocations[item.id] || []
        )
          .filter((a) => a.quantity > 0)
          .map((a) => ({
            locationType: a.locationType,
            locationId:
              a.locationType === LocationType.NOT_ASSIGNED
                ? undefined
                : a.locationId || undefined,
            quantity: a.quantity,
          }));

        return {
          shipmentItemId: item.id,
          allocations,
        };
      });

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
      const message =
        err instanceof Error ? err.message : "Failed to receive items";
      toast({ title: "Error", description: message });
    }
  }

  const isSaving = receiveMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Receive Items</DialogTitle>
          <DialogDescription>
            Record received quantities for shipment {shipment.shipmentNumber}.
            You can split items across multiple destinations.
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
              className="w-48"
            />
          </div>

          <div className="space-y-4">
            {shipment.items.map((item) => {
              const remaining = item.orderedQuantity - item.receivedQuantity;
              const isComplete = remaining <= 0;
              const allocated = getTotalAllocated(item.id);
              const isOverAllocated = allocated > remaining;
              const allocations = itemAllocations[item.id] || [];

              return (
                <div
                  key={item.id}
                  className={`rounded-lg border p-4 ${
                    isComplete ? "opacity-50" : ""
                  }`}
                >
                  <div className="flex items-start justify-between mb-3">
                    <div>
                      <div className="font-medium">{item.item.name}</div>
                      <div className="text-xs text-muted-foreground font-mono">
                        {item.item.sku}
                      </div>
                    </div>
                    <div className="text-right text-sm">
                      <div>
                        Ordered: <span className="font-medium">{item.orderedQuantity}</span>
                      </div>
                      <div>
                        Received: <span className="font-medium">{item.receivedQuantity}</span>
                      </div>
                      <div>
                        Remaining:{" "}
                        <span className="font-medium text-primary">
                          {remaining}
                        </span>
                      </div>
                    </div>
                  </div>

                  {!isComplete && (
                    <>
                      <div className="text-xs text-muted-foreground mb-2">
                        Allocations{" "}
                        <span
                          className={
                            isOverAllocated ? "text-destructive font-medium" : ""
                          }
                        >
                          (Total: {allocated}
                          {isOverAllocated && ` — exceeds remaining by ${allocated - remaining}`})
                        </span>
                      </div>

                      <div className="space-y-1 pl-2 border-l-2 border-muted">
                        {allocations.map((allocation) => (
                          <AllocationRow
                            key={allocation.id}
                            allocation={allocation}
                            maxQuantity={remaining}
                            onUpdate={(updates) =>
                              updateAllocation(item.id, allocation.id, updates)
                            }
                            onRemove={() =>
                              removeAllocation(item.id, allocation.id)
                            }
                            canRemove={allocations.length > 1}
                          />
                        ))}
                      </div>

                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="mt-2 h-7 text-xs"
                        onClick={() => addAllocation(item.id)}
                      >
                        <Plus className="h-3 w-3 mr-1" />
                        Add destination
                      </Button>
                    </>
                  )}
                </div>
              );
            })}
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
            disabled={isSaving || !hasAnyToReceive || hasValidationErrors}
          >
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Receive Items
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
