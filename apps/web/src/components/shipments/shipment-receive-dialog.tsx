"use client";

import { useEffect, useState, useMemo } from "react";
import { format } from "date-fns";
import { Calendar as CalendarIcon, Loader2, Plus, Trash2 } from "lucide-react";
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
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { cn } from "@/lib/utils";
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

interface DamagedQuantities {
  [itemId: string]: number;
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
    <div className="flex flex-col sm:flex-row sm:items-center gap-2 py-2 sm:py-1">
      <Select
        value={allocation.locationType}
        onValueChange={(value) =>
          onUpdate({ locationType: value as LocationType, locationId: "" })
        }
      >
        <SelectTrigger className="w-full sm:w-32 h-10 sm:h-8 text-xs">
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
          <SelectTrigger className="w-full sm:w-28 h-10 sm:h-8 text-xs">
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

      <div className="flex items-center gap-2">
        <Input
          type="number"
          min={0}
          max={maxQuantity}
          value={allocation.quantity}
          onChange={(e) => {
            const num = parseInt(e.target.value, 10);
            onUpdate({ quantity: Number.isNaN(num) ? 0 : Math.max(0, num) });
          }}
          className="flex-1 sm:w-20 sm:flex-none h-10 sm:h-8 text-xs text-right"
        />

        {canRemove && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-10 w-10 sm:h-8 sm:w-8 p-0 text-muted-foreground hover:text-destructive shrink-0"
            onClick={onRemove}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        )}
      </div>
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
  const [damagedQuantities, setDamagedQuantities] = useState<DamagedQuantities>({});

  // Reset form when dialog opens
  useEffect(() => {
    if (open && shipment) {
      setDeliveryDate(format(new Date(), "yyyy-MM-dd"));

      // Initialize each item with one default allocation and zero damaged
      const initialAllocations: ItemAllocations = {};
      const initialDamaged: DamagedQuantities = {};
      shipment.items.forEach((item) => {
        // Remaining = ordered - received - already damaged
        const remaining = item.orderedQuantity - item.receivedQuantity - (item.damagedQuantity || 0);
        initialAllocations[item.id] = [
          {
            id: crypto.randomUUID(),
            locationType: LocationType.NOT_ASSIGNED,
            locationId: "",
            quantity: remaining > 0 ? remaining : 0,
          },
        ];
        initialDamaged[item.id] = 0;
      });
      setItemAllocations(initialAllocations);
      setDamagedQuantities(initialDamaged);
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

  const getDamagedQuantity = (itemId: string) => {
    return damagedQuantities[itemId] || 0;
  };

  const setDamagedQuantity = (itemId: string, quantity: number) => {
    setDamagedQuantities((prev) => ({
      ...prev,
      [itemId]: Math.max(0, quantity),
    }));
  };

  const hasAnyToReceive = shipment.items.some(
    (item) => getTotalAllocated(item.id) > 0 || getDamagedQuantity(item.id) > 0
  );

  const hasValidationErrors = shipment.items.some((item) => {
    // Remaining = ordered - already received - already damaged
    const remaining = item.orderedQuantity - item.receivedQuantity - (item.damagedQuantity || 0);
    const allocated = getTotalAllocated(item.id);
    const damaged = getDamagedQuantity(item.id);
    return (allocated + damaged) > remaining;
  });

  async function handleSubmit() {
    if (!shipment) return;

    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }

    const itemReceipts: ShipmentItemReceipt[] = shipment.items
      .filter((item) => getTotalAllocated(item.id) > 0 || getDamagedQuantity(item.id) > 0)
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

        const damaged = getDamagedQuantity(item.id);

        return {
          shipmentItemId: item.id,
          allocations: allocations.length > 0 ? allocations : undefined,
          damagedQuantity: damaged > 0 ? damaged : undefined,
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
          receivedBy: actorId,
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
            <Label>Actual Delivery Date</Label>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  className={cn(
                    "w-full sm:w-48 justify-start text-left font-normal",
                    !deliveryDate && "text-muted-foreground"
                  )}
                >
                  {deliveryDate
                    ? format(new Date(deliveryDate + "T00:00:00"), "MM/dd/yyyy")
                    : <span>mm/dd/yyyy</span>}
                  <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <Calendar
                  mode="single"
                  selected={deliveryDate ? new Date(deliveryDate + "T00:00:00") : undefined}
                  onSelect={(date) => {
                    if (!date) { setDeliveryDate(""); return; }
                    const y = date.getUTCFullYear();
                    const m = String(date.getUTCMonth() + 1).padStart(2, "0");
                    const d = String(date.getUTCDate()).padStart(2, "0");
                    setDeliveryDate(`${y}-${m}-${d}`);
                  }}
                />
              </PopoverContent>
            </Popover>
          </div>

          <div className="space-y-4">
            {shipment.items.map((item) => {
              // Remaining = ordered - received - already damaged
              const remaining = item.orderedQuantity - item.receivedQuantity - (item.damagedQuantity || 0);
              const isComplete = remaining <= 0;
              const allocated = getTotalAllocated(item.id);
              const damaged = getDamagedQuantity(item.id);
              const isOverAllocated = (allocated + damaged) > remaining;
              const allocations = itemAllocations[item.id] || [];

              return (
                <div
                  key={item.id}
                  className={`rounded-lg border p-4 ${
                    isComplete ? "opacity-50" : ""
                  }`}
                >
                  <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-2 mb-3">
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
                      {(item.damagedQuantity || 0) > 0 && (
                        <div>
                          Damaged: <span className="font-medium text-amber-600">{item.damagedQuantity}</span>
                        </div>
                      )}
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
                          (Good: {allocated}{damaged > 0 ? `, Damaged: ${damaged}` : ""}
                          {isOverAllocated && ` — exceeds remaining by ${allocated + damaged - remaining}`})
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

                      <div className="mt-3 pt-3 border-t">
                        <div className="flex items-center gap-3">
                          <Label htmlFor={`damaged-${item.id}`} className="text-xs text-muted-foreground whitespace-nowrap">
                            Damaged items:
                          </Label>
                          <Input
                            id={`damaged-${item.id}`}
                            type="number"
                            min={0}
                            max={remaining}
                            value={getDamagedQuantity(item.id)}
                            onChange={(e) => {
                              const num = parseInt(e.target.value, 10);
                              setDamagedQuantity(item.id, Number.isNaN(num) ? 0 : num);
                            }}
                            className="w-20 h-8 text-xs text-right"
                          />
                          {getDamagedQuantity(item.id) > 0 && (
                            <span className="text-xs text-amber-600">
                              Won&apos;t be added to inventory
                            </span>
                          )}
                        </div>
                      </div>
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
