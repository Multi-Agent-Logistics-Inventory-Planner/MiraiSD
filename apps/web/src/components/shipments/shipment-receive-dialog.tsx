"use client";

import { useEffect, useState, useMemo } from "react";
import Image from "next/image";
import { format } from "date-fns";
import { Calendar as CalendarIcon, Loader2, Plus, Trash2, Package } from "lucide-react";
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
import { cn, prizeLetterDisplay, sortPrizes } from "@/lib/utils";
import { useAuth } from "@/hooks/use-auth";
import { useReceiveShipmentMutation } from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import { useLocations } from "@/hooks/queries/use-locations";
import type {
  Shipment,
  ShipmentItem,
  ShipmentItemReceipt,
  DestinationAllocation,
  StorageLocation,
  BoxBin,
  Cabinet,
  DoubleClawMachine,
  FourCornerMachine,
  Gachapon,
  KeychainMachine,
  PusherMachine,
  Rack,
  SingleClawMachine,
  Window,
} from "@/types/api";
import { LocationType } from "@/types/api";

function getLocationCode(location: StorageLocation): string {
  if ("boxBinCode" in location) return (location as BoxBin).boxBinCode;
  if ("cabinetCode" in location) return (location as Cabinet).cabinetCode;
  if ("doubleClawMachineCode" in location)
    return (location as DoubleClawMachine).doubleClawMachineCode;
  if ("fourCornerMachineCode" in location)
    return (location as FourCornerMachine).fourCornerMachineCode;
  if ("gachaponCode" in location) return (location as Gachapon).gachaponCode;
  if ("keychainMachineCode" in location)
    return (location as KeychainMachine).keychainMachineCode;
  if ("pusherMachineCode" in location)
    return (location as PusherMachine).pusherMachineCode;
  if ("rackCode" in location) return (location as Rack).rackCode;
  if ("singleClawMachineCode" in location)
    return (location as SingleClawMachine).singleClawMachineCode;
  if ("windowCode" in location) return (location as Window).windowCode;
  return "";
}

const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  [LocationType.BOX_BIN]: "Box/Bin",
  [LocationType.CABINET]: "Cabinet",
  [LocationType.DOUBLE_CLAW_MACHINE]: "Double Claw",
  [LocationType.FOUR_CORNER_MACHINE]: "Four Corner",
  [LocationType.GACHAPON]: "Gachapon",
  [LocationType.KEYCHAIN_MACHINE]: "Keychain",
  [LocationType.PUSHER_MACHINE]: "Pusher",
  [LocationType.RACK]: "Rack",
  [LocationType.SINGLE_CLAW_MACHINE]: "Single Claw",
  [LocationType.WINDOW]: "Window",
  [LocationType.NOT_ASSIGNED]: "Not Assigned",
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

interface DisplayQuantities {
  [itemId: string]: number;
}

interface ShopQuantities {
  [itemId: string]: number;
}

/** Prize items in a Kuji block use parent's receive location; we only store quantity per prize. */
interface PrizeReceivedQuantities {
  [shipmentItemId: string]: number;
}

/** Track number of sets received per kuji parent (for auto-calculating prize quantities) */
interface SetsReceived {
  [parentShipmentItemId: string]: number;
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
  const [displayQuantities, setDisplayQuantities] = useState<DisplayQuantities>({});
  const [shopQuantities, setShopQuantities] = useState<ShopQuantities>({});
  const [prizeReceivedQuantities, setPrizeReceivedQuantities] = useState<PrizeReceivedQuantities>({});
  const [setsReceived, setSetsReceived] = useState<SetsReceived>({});

  // Reset form when dialog opens
  useEffect(() => {
    if (open && shipment) {
      setDeliveryDate(format(new Date(), "yyyy-MM-dd"));

      const initialAllocations: ItemAllocations = {};
      const initialDamaged: DamagedQuantities = {};
      const initialDisplay: DisplayQuantities = {};
      const initialShop: ShopQuantities = {};
      const initialPrizeQtys: PrizeReceivedQuantities = {};
      shipment.items.forEach((item) => {
        const isPrize = !!item.item.parentId;
        if (isPrize) {
          initialPrizeQtys[item.id] = 0;
        } else {
          initialAllocations[item.id] = [
            {
              id: crypto.randomUUID(),
              locationType: LocationType.NOT_ASSIGNED,
              locationId: "",
              quantity: 0,
            },
          ];
        }
        initialDamaged[item.id] = 0;
        initialDisplay[item.id] = 0;
        initialShop[item.id] = 0;
      });
      setItemAllocations(initialAllocations);
      setDamagedQuantities(initialDamaged);
      setDisplayQuantities(initialDisplay);
      setShopQuantities(initialShop);
      setPrizeReceivedQuantities(initialPrizeQtys);
      setSetsReceived({});
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

  /** For prizes we use prizeReceivedQuantities (same location as parent); for roots use itemAllocations. */
  const getEffectiveTotalAllocated = (item: ShipmentItem) => {
    if (item.item.parentId) return prizeReceivedQuantities[item.id] ?? 0;
    return getTotalAllocated(item.id);
  };

  const setPrizeReceivedQuantity = (shipmentItemId: string, quantity: number) => {
    setPrizeReceivedQuantities((prev) => ({
      ...prev,
      [shipmentItemId]: Math.max(0, quantity),
    }));
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

  const getDisplayQuantity = (itemId: string) => {
    return displayQuantities[itemId] || 0;
  };

  const setDisplayQuantity = (itemId: string, quantity: number) => {
    setDisplayQuantities((prev) => ({
      ...prev,
      [itemId]: Math.max(0, quantity),
    }));
  };

  const getShopQuantity = (itemId: string) => {
    return shopQuantities[itemId] || 0;
  };

  const setShopQuantity = (itemId: string, quantity: number) => {
    setShopQuantities((prev) => ({
      ...prev,
      [itemId]: Math.max(0, quantity),
    }));
  };

  const hasAnyToReceive = shipment.items.some(
    (item) => getEffectiveTotalAllocated(item) > 0 || getDamagedQuantity(item.id) > 0 ||
      getDisplayQuantity(item.id) > 0 || getShopQuantity(item.id) > 0
  );

  const hasValidationErrors = shipment.items.some((item) => {
    const remaining = item.orderedQuantity - item.receivedQuantity -
      (item.damagedQuantity || 0) - (item.displayQuantity || 0) - (item.shopQuantity || 0);
    const allocated = getEffectiveTotalAllocated(item);
    const damaged = getDamagedQuantity(item.id);
    const display = getDisplayQuantity(item.id);
    const shop = getShopQuantity(item.id);
    return (allocated + damaged + display + shop) > remaining;
  });

  async function handleSubmit() {
    if (!shipment) return;

    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }

    const itemReceipts: ShipmentItemReceipt[] = shipment.items
      .filter((item) => getEffectiveTotalAllocated(item) > 0 || getDamagedQuantity(item.id) > 0 ||
        getDisplayQuantity(item.id) > 0 || getShopQuantity(item.id) > 0)
      .map((item) => {
        const damaged = getDamagedQuantity(item.id);
        const display = getDisplayQuantity(item.id);
        const shop = getShopQuantity(item.id);
        let allocations: DestinationAllocation[];

        if (item.item.parentId) {
          // Prize: use parent shipment item's location(s) with this prize's quantity (itemAllocations is keyed by shipment item id)
          const parentShipmentItem = shipment.items.find((i) => i.item.id === item.item.parentId);
          const parentAllocations = (parentShipmentItem ? (itemAllocations[parentShipmentItem.id] || []) : []).filter((a) => a.quantity > 0);
          const prizeQty = prizeReceivedQuantities[item.id] ?? 0;
          if (prizeQty <= 0) {
            allocations = [];
          } else if (parentAllocations.length === 0) {
            // No parent in shipment or parent has no allocations - default to NOT_ASSIGNED
            allocations = [
              {
                locationType: LocationType.NOT_ASSIGNED,
                locationId: undefined,
                quantity: prizeQty,
              },
            ];
          } else {
            // Same location(s) as parent; for single-destination we use prize qty; if multiple we put all prize qty in first (or split - here we use first only for simplicity)
            const first = parentAllocations[0];
            allocations = [
              {
                locationType: first.locationType,
                locationId: first.locationType === LocationType.NOT_ASSIGNED ? undefined : first.locationId || undefined,
                quantity: prizeQty,
              },
            ];
          }
        } else {
          allocations = (itemAllocations[item.id] || [])
            .filter((a) => a.quantity > 0)
            .map((a) => ({
              locationType: a.locationType,
              locationId: a.locationType === LocationType.NOT_ASSIGNED ? undefined : a.locationId || undefined,
              quantity: a.quantity,
            }));
        }

        return {
          shipmentItemId: item.id,
          allocations: allocations.length > 0 ? allocations : undefined,
          damagedQuantity: damaged > 0 ? damaged : undefined,
          displayQuantity: display > 0 ? display : undefined,
          shopQuantity: shop > 0 ? shop : undefined,
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
            {(() => {
              // Group items: root items (no parentId) and their child prizes
              const rootItems = shipment.items.filter((i) => !i.item.parentId);
              const prizesByParentId: Record<string, ShipmentItem[]> = {};
              shipment.items.forEach((i) => {
                const pid = i.item.parentId;
                if (pid) {
                  if (!prizesByParentId[pid]) prizesByParentId[pid] = [];
                  prizesByParentId[pid].push(i);
                }
              });

              return rootItems.map((item) => {
                const remaining = item.orderedQuantity - item.receivedQuantity -
                  (item.damagedQuantity || 0) - (item.displayQuantity || 0) - (item.shopQuantity || 0);
                const isComplete = remaining <= 0;
                const allocated = getTotalAllocated(item.id);
                const damaged = getDamagedQuantity(item.id);
                const display = getDisplayQuantity(item.id);
                const shop = getShopQuantity(item.id);
                const totalNonInventory = damaged + display + shop;
                const isOverAllocated = (allocated + totalNonInventory) > remaining;
                const allocations = itemAllocations[item.id] || [];
                const itemImageUrl = item.item?.imageUrl;

                // Get sorted child prizes for this parent
                const childPrizes = sortPrizes(
                  (prizesByParentId[item.item.id] ?? []).map((p) => ({
                    ...p,
                    letter: (p.item as { letter?: string | null }).letter,
                  }))
                );
                const hasChildren = childPrizes.length > 0;

                return (
                  <div
                    key={item.id}
                    className={`rounded-lg border p-4 ${isComplete && childPrizes.every((p) => {
                      const pRemaining = p.orderedQuantity - p.receivedQuantity -
                        (p.damagedQuantity || 0) - (p.displayQuantity || 0) - (p.shopQuantity || 0);
                      return pRemaining <= 0;
                    }) ? "opacity-50" : ""}`}
                  >
                    {/* Parent item header */}
                    <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-2 mb-3">
                      <div className="flex items-start gap-3">
                        <div className="relative h-10 w-10 rounded-lg bg-muted overflow-hidden flex items-center justify-center shrink-0">
                          {itemImageUrl ? (
                            <Image
                              src={itemImageUrl}
                              alt={item.item.name}
                              fill
                              sizes="40px"
                              className="object-cover"
                            />
                          ) : (
                            <Package className="h-4 w-4 text-muted-foreground" />
                          )}
                        </div>
                        <div>
                          <div className="font-medium">{item.item.name}</div>
                          <div className="text-xs text-muted-foreground font-mono">
                            {item.item.sku}
                          </div>
                        </div>
                      </div>
                      <div className="text-right text-sm">
                        <div>Ordered: <span className="font-medium">{item.orderedQuantity}</span></div>
                        <div>Received: <span className="font-medium">{item.receivedQuantity}</span></div>
                        {(item.damagedQuantity || 0) > 0 && (
                          <div>Damaged: <span className="font-medium text-amber-600">{item.damagedQuantity}</span></div>
                        )}
                        {(item.displayQuantity || 0) > 0 && (
                          <div>Display: <span className="font-medium text-blue-600">{item.displayQuantity}</span></div>
                        )}
                        {(item.shopQuantity || 0) > 0 && (
                          <div>Shop: <span className="font-medium text-green-600">{item.shopQuantity}</span></div>
                        )}
                        <div>Remaining: <span className="font-medium text-primary">{remaining}</span></div>
                      </div>
                    </div>

                    {/* Parent item allocation UI */}
                    {!isComplete && (
                      <>
                        <div className="text-xs text-muted-foreground mb-2">
                          Allocations{" "}
                          <span className={isOverAllocated ? "text-destructive font-medium" : ""}>
                            (Good: {allocated}
                            {damaged > 0 && <>, <span className="text-amber-600">Damaged: {damaged}</span></>}
                            {display > 0 && <>, <span className="text-blue-600">Display: {display}</span></>}
                            {shop > 0 && <>, <span className="text-green-600">Shop: {shop}</span></>}
                            {isOverAllocated && ` — exceeds remaining by ${allocated + totalNonInventory - remaining}`})
                          </span>
                        </div>
                        <div className="space-y-1 pl-2 border-l-2 border-muted">
                          {allocations.map((allocation) => (
                            <AllocationRow
                              key={allocation.id}
                              allocation={allocation}
                              maxQuantity={remaining}
                              onUpdate={(updates) => updateAllocation(item.id, allocation.id, updates)}
                              onRemove={() => removeAllocation(item.id, allocation.id)}
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
                        <div className="mt-3 pt-3 border-t space-y-2">
                          <div className="flex items-center gap-3">
                            <Label htmlFor={`damaged-${item.id}`} className="text-xs text-muted-foreground whitespace-nowrap w-20">Damaged:</Label>
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
                              <span className="text-xs text-amber-600">Won&apos;t be added to inventory</span>
                            )}
                          </div>
                          <div className="flex items-center gap-3">
                            <Label htmlFor={`display-${item.id}`} className="text-xs text-muted-foreground whitespace-nowrap w-20">Display:</Label>
                            <Input
                              id={`display-${item.id}`}
                              type="number"
                              min={0}
                              max={remaining}
                              value={getDisplayQuantity(item.id)}
                              onChange={(e) => {
                                const num = parseInt(e.target.value, 10);
                                setDisplayQuantity(item.id, Number.isNaN(num) ? 0 : num);
                              }}
                              className="w-20 h-8 text-xs text-right"
                            />
                            {getDisplayQuantity(item.id) > 0 && (
                              <span className="text-xs text-blue-600">For display use</span>
                            )}
                          </div>
                          <div className="flex items-center gap-3">
                            <Label htmlFor={`shop-${item.id}`} className="text-xs text-muted-foreground whitespace-nowrap w-20">Shop:</Label>
                            <Input
                              id={`shop-${item.id}`}
                              type="number"
                              min={0}
                              max={remaining}
                              value={getShopQuantity(item.id)}
                              onChange={(e) => {
                                const num = parseInt(e.target.value, 10);
                                setShopQuantity(item.id, Number.isNaN(num) ? 0 : num);
                              }}
                              className="w-20 h-8 text-xs text-right"
                            />
                            {getShopQuantity(item.id) > 0 && (
                              <span className="text-xs text-green-600">For shop use</span>
                            )}
                          </div>
                        </div>
                      </>
                    )}

                    {/* Child prizes section */}
                    {hasChildren && (
                      <div className="mt-4 pt-4 border-t">
                        <div className="flex items-center justify-between mb-3">
                          <div className="text-xs font-medium text-muted-foreground">
                            Prizes for {item.item.name}
                          </div>
                          {/* Sets input for auto-calculation */}
                          <div className="flex items-center gap-2">
                            <Label className="text-xs text-muted-foreground">Sets:</Label>
                            <Input
                              type="number"
                              min={0}
                              value={setsReceived[item.id] ?? ""}
                              onChange={(e) => {
                                const sets = parseInt(e.target.value, 10) || 0;
                                setSetsReceived((prev) => ({ ...prev, [item.id]: sets }));

                                // Update parent Kuji item quantity to match sets
                                const parentAllocations = itemAllocations[item.id] || [];
                                if (parentAllocations.length > 0) {
                                  // Update first allocation's quantity
                                  const cappedSets = Math.min(sets, remaining);
                                  updateAllocation(item.id, parentAllocations[0].id, { quantity: cappedSets });
                                }

                                // Auto-calculate prize quantities based on templateQuantity
                                // Prizes only support damaged (not display/shop)
                                childPrizes.forEach((prize) => {
                                  const prizeItem = prize.item as { templateQuantity?: number | null };
                                  const templateQty = prizeItem.templateQuantity ?? 0;
                                  const calculatedQty = templateQty * sets;
                                  const prizeRemaining = prize.orderedQuantity - prize.receivedQuantity -
                                    (prize.damagedQuantity || 0);
                                  // Cap at remaining quantity
                                  setPrizeReceivedQuantity(prize.id, Math.min(calculatedQty, prizeRemaining));
                                });
                              }}
                              className="w-16 h-7 text-xs"
                              placeholder="0"
                            />
                            <span className="text-xs text-muted-foreground">(auto-fills)</span>
                          </div>
                        </div>
                        <div className="space-y-3 pl-4 border-l-2 border-primary/20">
                          {childPrizes.map((prize) => {
                            // Prizes only support damaged (not display/shop)
                            const prizeRemaining = prize.orderedQuantity - prize.receivedQuantity -
                              (prize.damagedQuantity || 0);
                            const prizeIsComplete = prizeRemaining <= 0;
                            const prizeQty = prizeReceivedQuantities[prize.id] ?? 0;
                            const prizeDamaged = getDamagedQuantity(prize.id);
                            const prizeIsOver = (prizeQty + prizeDamaged) > prizeRemaining;
                            const letter = prize.letter;
                            const prizeLabel = letter ? prizeLetterDisplay(letter) : "?";
                            const prizeItem = prize.item as { templateQuantity?: number | null };
                            const templateQty = prizeItem.templateQuantity;

                            return (
                              <div
                                key={prize.id}
                                className={`py-2 ${prizeIsComplete ? "opacity-50" : ""}`}
                              >
                                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                                  <div className="flex items-center gap-2 flex-wrap">
                                    <span className="font-mono font-bold text-sm bg-muted px-2 py-0.5 rounded">
                                      {prizeLabel}
                                    </span>
                                    {templateQty && (
                                      <span className="text-xs text-muted-foreground">
                                        ({templateQty}/set)
                                      </span>
                                    )}
                                    <span className="text-xs text-muted-foreground">
                                      {prize.orderedQuantity} ordered, {prize.receivedQuantity} received
                                    </span>
                                    {prizeRemaining > 0 && (
                                      <span className="text-xs text-primary font-medium">
                                        ({prizeRemaining} remaining)
                                      </span>
                                    )}
                                  </div>
                                </div>
                                {!prizeIsComplete && (
                                  <div className="mt-2 flex items-center gap-2 flex-wrap">
                                    <Label className="text-xs text-muted-foreground">Qty:</Label>
                                    <Input
                                      type="number"
                                      min={0}
                                      max={prizeRemaining}
                                      value={prizeQty}
                                      onChange={(e) => {
                                        const num = parseInt(e.target.value, 10);
                                        setPrizeReceivedQuantity(prize.id, Number.isNaN(num) ? 0 : num);
                                      }}
                                      className="w-16 h-7 text-xs text-right"
                                    />
                                    <Label className="text-xs text-muted-foreground">Dmg:</Label>
                                    <Input
                                      type="number"
                                      min={0}
                                      max={prizeRemaining}
                                      value={prizeDamaged}
                                      onChange={(e) => {
                                        const num = parseInt(e.target.value, 10);
                                        setDamagedQuantity(prize.id, Number.isNaN(num) ? 0 : num);
                                      }}
                                      className="w-14 h-7 text-xs text-right"
                                    />
                                    {prizeIsOver && (
                                      <span className="text-xs text-destructive font-medium">
                                        Exceeds by {prizeQty + prizeDamaged - prizeRemaining}
                                      </span>
                                    )}
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    )}
                  </div>
                );
              });
            })()}
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
