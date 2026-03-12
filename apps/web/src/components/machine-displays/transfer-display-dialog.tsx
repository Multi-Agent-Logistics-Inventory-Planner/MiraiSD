"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import {
  ArrowLeftRight,
  Check,
  ChevronRight,
  ImageOff,
  Loader2,
  Monitor,
  Package,
} from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";
import {
  LocationType,
  MachineDisplay,
  StorageLocation,
  SingleClawMachine,
  DoubleClawMachine,
  KeychainMachine,
  FourCornerMachine,
  PusherMachine,
  Product,
} from "@/types/api";
import { useQuery } from "@tanstack/react-query";
import { useLocationsOnly } from "@/hooks/queries/use-locations";
import {
  useActiveDisplaysForMachine,
  useActiveDisplaysByType,
} from "@/hooks/queries/use-machine-displays";
import { getProducts } from "@/lib/api/products";

type SwapMode = "machine" | "products";
type Step = "select-mode" | "select-machine" | "select-items" | "select-products";

interface TransferDisplayDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  locationType: LocationType;
  currentMachineId: string;
  currentMachineCode: string;
  currentDisplays: MachineDisplay[];
  actorId?: string;
  onTransferWithMachine: (
    itemsToSend: MachineDisplay[],
    itemsToReceive: MachineDisplay[],
    targetMachineId: string
  ) => Promise<void>;
  onSwapWithProducts: (
    itemsToRemove: MachineDisplay[],
    productsToAdd: string[]
  ) => Promise<void>;
  isSubmitting?: boolean;
}

function getMachineCode(locationType: LocationType, loc: StorageLocation): string {
  switch (locationType) {
    case LocationType.SINGLE_CLAW_MACHINE:
      return (loc as SingleClawMachine).singleClawMachineCode;
    case LocationType.DOUBLE_CLAW_MACHINE:
      return (loc as DoubleClawMachine).doubleClawMachineCode;
    case LocationType.KEYCHAIN_MACHINE:
      return (loc as KeychainMachine).keychainMachineCode;
    case LocationType.FOUR_CORNER_MACHINE:
      return (loc as FourCornerMachine).fourCornerMachineCode;
    case LocationType.PUSHER_MACHINE:
      return (loc as PusherMachine).pusherMachineCode;
    default:
      return loc.id;
  }
}

interface DisplayItemProps {
  display: MachineDisplay;
  selected: boolean;
  onToggle: () => void;
  disabled?: boolean;
  direction: "send" | "receive";
  imageUrl?: string | null;
}

function DisplayItem({ display, selected, onToggle, disabled, direction, imageUrl }: DisplayItemProps) {
  const [imageError, setImageError] = useState(false);
  const safeImageUrl = getSafeImageUrl(imageUrl);
  const hasImage = safeImageUrl && !imageError;

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      className={cn(
        "w-full flex items-center gap-3 p-3 rounded-lg border transition-colors text-left overflow-hidden",
        selected
          ? direction === "send"
            ? "bg-orange-50 border-orange-300 dark:bg-orange-950/30 dark:border-orange-700"
            : "bg-green-50 border-green-300 dark:bg-green-950/30 dark:border-green-700"
          : "bg-background hover:bg-muted/50 border-border",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="relative h-10 w-10 shrink-0 rounded-md overflow-hidden bg-muted">
        {hasImage ? (
          <Image
            src={safeImageUrl}
            alt={display.productName}
            fill
            sizes="40px"
            className="object-cover"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <ImageOff className="h-4 w-4 text-muted-foreground" />
          </div>
        )}
      </div>

      <div className="flex-1 w-0">
        <p className="text-sm font-medium truncate">{display.productName}</p>
        <p className="text-xs text-muted-foreground truncate">{display.productSku}</p>
      </div>

      <div
        className={cn(
          "h-5 w-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-colors",
          selected
            ? direction === "send"
              ? "bg-orange-500 border-orange-500"
              : "bg-green-500 border-green-500"
            : "border-muted-foreground/30"
        )}
      >
        {selected && <Check className="h-3 w-3 text-white" />}
      </div>
    </button>
  );
}

interface ProductItemProps {
  product: Product;
  selected: boolean;
  onToggle: () => void;
  disabled?: boolean;
}

function ProductItem({ product, selected, onToggle, disabled }: ProductItemProps) {
  const [imageError, setImageError] = useState(false);
  const safeImageUrl = getSafeImageUrl(product.imageUrl);
  const hasImage = safeImageUrl && !imageError;

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      className={cn(
        "w-full flex items-center gap-3 p-3 rounded-lg border transition-colors text-left overflow-hidden",
        selected
          ? "bg-green-50 border-green-300 dark:bg-green-950/30 dark:border-green-700"
          : "bg-background hover:bg-muted/50 border-border",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="relative h-10 w-10 shrink-0 rounded-md overflow-hidden bg-muted">
        {hasImage ? (
          <Image
            src={safeImageUrl}
            alt={product.name}
            fill
            sizes="40px"
            className="object-cover"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <ImageOff className="h-4 w-4 text-muted-foreground" />
          </div>
        )}
      </div>

      <div className="flex-1 w-0">
        <p className="text-sm font-medium truncate">{product.name}</p>
        <Badge variant="outline" className="text-[10px] mt-0.5">
          {product.category.name}
        </Badge>
      </div>

      <div
        className={cn(
          "h-5 w-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-colors",
          selected
            ? "bg-green-500 border-green-500"
            : "border-muted-foreground/30"
        )}
      >
        {selected && <Check className="h-3 w-3 text-white" />}
      </div>
    </button>
  );
}

interface MachineCardProps {
  machine: StorageLocation;
  locationType: LocationType;
  selected: boolean;
  onSelect: () => void;
  disabled?: boolean;
  displayCount: number;
}

function MachineCard({
  machine,
  locationType,
  selected,
  onSelect,
  disabled,
  displayCount,
}: MachineCardProps) {
  const code = getMachineCode(locationType, machine);

  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={disabled}
      className={cn(
        "w-full flex items-center gap-3 p-4 rounded-lg border transition-colors text-left overflow-hidden",
        selected
          ? "bg-primary/5 border-primary"
          : "bg-background hover:bg-muted/50 border-border",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="flex-1 w-0">
        <p className="font-medium truncate">{code}</p>
        <p className="text-sm text-muted-foreground truncate">
          {displayCount} product{displayCount !== 1 ? "s" : ""} on display
        </p>
      </div>

      {selected ? (
        <div className="h-6 w-6 rounded-full bg-primary flex items-center justify-center shrink-0">
          <Check className="h-4 w-4 text-primary-foreground" />
        </div>
      ) : (
        <ChevronRight className="h-5 w-5 text-muted-foreground shrink-0" />
      )}
    </button>
  );
}

interface ModeCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  onClick: () => void;
}

function ModeCard({ icon, title, description, onClick }: ModeCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-center gap-4 p-4 rounded-lg border bg-background hover:bg-muted/50 border-border transition-colors text-left"
    >
      <div className="h-12 w-12 rounded-lg bg-muted flex items-center justify-center shrink-0">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="font-medium">{title}</p>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      <ChevronRight className="h-5 w-5 text-muted-foreground shrink-0" />
    </button>
  );
}

export function TransferDisplayDialog({
  open,
  onOpenChange,
  locationType,
  currentMachineId,
  currentMachineCode,
  currentDisplays,
  onTransferWithMachine,
  onSwapWithProducts,
  isSubmitting = false,
}: TransferDisplayDialogProps) {
  const [step, setStep] = useState<Step>("select-mode");
  const [mode, setMode] = useState<SwapMode | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedMachineId, setSelectedMachineId] = useState<string | null>(null);
  const [itemsToSend, setItemsToSend] = useState<Set<string>>(new Set());
  const [itemsToReceive, setItemsToReceive] = useState<Set<string>>(new Set());
  const [productsToAdd, setProductsToAdd] = useState<Set<string>>(new Set());

  // Fetch products (rootOnly to exclude child products/prizes)
  const { data: products = [] } = useQuery({
    queryKey: ["products", { rootOnly: true }],
    queryFn: () => getProducts(true),
  });

  // Fetch all machines of the same type
  const { data: machines = [], isLoading: isMachinesLoading } = useLocationsOnly(locationType);

  // Fetch displays for the selected target machine
  const { data: targetDisplays = [], isLoading: isTargetDisplaysLoading } =
    useActiveDisplaysForMachine(
      selectedMachineId ? locationType : undefined,
      selectedMachineId ?? undefined
    );

  // Get display counts for each machine
  const { data: allDisplaysByType = [] } = useActiveDisplaysByType(locationType);

  // Create a map of machine ID to display count
  const displayCountByMachine = useMemo(() => {
    const map = new Map<string, number>();
    for (const display of allDisplaysByType) {
      const count = map.get(display.machineId) ?? 0;
      map.set(display.machineId, count + 1);
    }
    map.set(currentMachineId, currentDisplays.length);
    return map;
  }, [allDisplaysByType, currentMachineId, currentDisplays.length]);

  // Filter machines (exclude current machine)
  const availableMachines = useMemo(() => {
    return machines
      .filter((m) => m.id !== currentMachineId)
      .filter((m) => {
        if (!searchQuery.trim()) return true;
        const code = getMachineCode(locationType, m).toLowerCase();
        return code.includes(searchQuery.toLowerCase().trim());
      });
  }, [machines, currentMachineId, searchQuery, locationType]);

  // Products not already on the current machine's display
  const currentDisplayProductIds = useMemo(() => {
    return new Set(currentDisplays.map((d) => d.productId));
  }, [currentDisplays]);

  const availableProducts = useMemo(() => {
    return products
      .filter((p) => !currentDisplayProductIds.has(p.id))
      .filter((p) => {
        if (!searchQuery.trim()) return true;
        return p.name.toLowerCase().includes(searchQuery.toLowerCase().trim());
      });
  }, [products, currentDisplayProductIds, searchQuery]);

  // Map product IDs to image URLs for display items
  const productImageMap = useMemo(() => {
    const map = new Map<string, string | null>();
    for (const product of products) {
      map.set(product.id, product.imageUrl ?? null);
    }
    return map;
  }, [products]);

  const selectedMachine = machines.find((m) => m.id === selectedMachineId);
  const selectedMachineCode = selectedMachine
    ? getMachineCode(locationType, selectedMachine)
    : "";

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setStep("select-mode");
      setMode(null);
      setSearchQuery("");
      setSelectedMachineId(null);
      setItemsToSend(new Set());
      setItemsToReceive(new Set());
      setProductsToAdd(new Set());
    }
  }, [open]);

  function handleModeSelect(selectedMode: SwapMode) {
    setMode(selectedMode);
    if (selectedMode === "machine") {
      setStep("select-machine");
    } else {
      setStep("select-products");
    }
  }

  function handleMachineSelect(machineId: string) {
    setSelectedMachineId(machineId);
    setStep("select-items");
  }

  function handleBack() {
    if (step === "select-machine" || step === "select-products") {
      setStep("select-mode");
      setMode(null);
      setSearchQuery("");
    } else if (step === "select-items") {
      setStep("select-machine");
      setSelectedMachineId(null);
      setItemsToSend(new Set());
      setItemsToReceive(new Set());
    }
  }

  function toggleItemToSend(displayId: string) {
    setItemsToSend((prev) => {
      const next = new Set(prev);
      if (next.has(displayId)) {
        next.delete(displayId);
      } else {
        next.add(displayId);
      }
      return next;
    });
  }

  function toggleItemToReceive(displayId: string) {
    setItemsToReceive((prev) => {
      const next = new Set(prev);
      if (next.has(displayId)) {
        next.delete(displayId);
      } else {
        next.add(displayId);
      }
      return next;
    });
  }

  function toggleProductToAdd(productId: string) {
    setProductsToAdd((prev) => {
      const next = new Set(prev);
      if (next.has(productId)) {
        next.delete(productId);
      } else {
        next.add(productId);
      }
      return next;
    });
  }

  async function handleTransfer() {
    if (mode === "machine" && selectedMachineId) {
      const sending = currentDisplays.filter((d) => itemsToSend.has(d.id));
      const receiving = targetDisplays.filter((d) => itemsToReceive.has(d.id));
      if (sending.length === 0 && receiving.length === 0) return;
      await onTransferWithMachine(sending, receiving, selectedMachineId);
    } else if (mode === "products") {
      const removing = currentDisplays.filter((d) => itemsToSend.has(d.id));
      const adding = Array.from(productsToAdd);
      if (removing.length === 0 && adding.length === 0) return;
      await onSwapWithProducts(removing, adding);
    }
  }

  const hasSelectionsForMachine = itemsToSend.size > 0 || itemsToReceive.size > 0;
  const hasSelectionsForProducts = itemsToSend.size > 0 || productsToAdd.size > 0;
  const canTransfer =
    !isSubmitting &&
    ((mode === "machine" && hasSelectionsForMachine) ||
      (mode === "products" && hasSelectionsForProducts));

  function getTitle() {
    switch (step) {
      case "select-mode":
        return "Swap Display";
      case "select-machine":
        return "Select Machine";
      case "select-items":
        return "Select Items to Transfer";
      case "select-products":
        return "Swap with Products";
      default:
        return "Swap Display";
    }
  }

  function getDescription() {
    switch (step) {
      case "select-mode":
        return "Choose how you want to swap display items.";
      case "select-machine":
        return "Select another machine to transfer display items with.";
      case "select-items":
        return `Trade display items between ${currentMachineCode} and ${selectedMachineCode}.`;
      case "select-products":
        return "Remove items from display and/or add new products.";
      default:
        return "";
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl h-[85dvh] max-h-[85dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>{getTitle()}</DialogTitle>
          <DialogDescription>{getDescription()}</DialogDescription>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col px-6 py-4">
          {step === "select-mode" && (
            <div className="space-y-3">
              <ModeCard
                icon={<Monitor className="h-6 w-6 text-muted-foreground" />}
                title="Swap with another machine"
                description="Trade display items between two machines"
                onClick={() => handleModeSelect("machine")}
              />
              <ModeCard
                icon={<Package className="h-6 w-6 text-muted-foreground" />}
                title="Swap with products"
                description="Replace display items with products from inventory"
                onClick={() => handleModeSelect("products")}
              />
            </div>
          )}

          {step === "select-machine" && (
            <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
              <div className="shrink-0 mb-3">
                <Label className="text-xs text-muted-foreground mb-2 block">
                  Select a machine ({availableMachines.length})
                </Label>
                <Input
                  type="text"
                  placeholder="Search machines..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="h-9"
                />
              </div>

              {isMachinesLoading ? (
                <div className="flex-1 flex items-center justify-center">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : availableMachines.length === 0 ? (
                <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                  {searchQuery
                    ? "No machines match your search"
                    : "No other machines available"}
                </div>
              ) : (
                <div className="flex-1 min-h-0 overflow-hidden">
                  <ScrollArea className="h-full">
                    <div className="space-y-2 pr-3 pb-2">
                      {availableMachines.map((machine) => (
                        <MachineCard
                          key={machine.id}
                          machine={machine}
                          locationType={locationType}
                          selected={selectedMachineId === machine.id}
                          onSelect={() => handleMachineSelect(machine.id)}
                          displayCount={displayCountByMachine.get(machine.id) ?? 0}
                        />
                      ))}
                    </div>
                  </ScrollArea>
                </div>
              )}
            </div>
          )}

          {step === "select-items" && (
            <>
              {/* Two-column layout for machine-to-machine transfer */}
              <div className="flex-1 min-h-0 grid grid-cols-2 gap-4 overflow-hidden">
                {/* Left column: Current machine items to send */}
                <div className="flex flex-col min-h-0 min-w-0 overflow-hidden">
                  <div className="shrink-0 mb-2">
                    <Label className="text-xs text-muted-foreground">
                      From {currentMachineCode} ({currentDisplays.length})
                    </Label>
                    <p className="text-xs text-orange-600 dark:text-orange-400">
                      {itemsToSend.size > 0
                        ? `Sending ${itemsToSend.size} item${itemsToSend.size !== 1 ? "s" : ""}`
                        : "Select items to send"}
                    </p>
                  </div>

                  {currentDisplays.length === 0 ? (
                    <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                      No items on display
                    </div>
                  ) : (
                    <div className="flex-1 min-h-0 overflow-hidden">
                      <ScrollArea className="h-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
                        <div className="space-y-2 pr-3 pb-2">
                          {currentDisplays.map((display) => (
                            <DisplayItem
                              key={display.id}
                              display={display}
                              selected={itemsToSend.has(display.id)}
                              onToggle={() => toggleItemToSend(display.id)}
                              direction="send"
                              disabled={isSubmitting}
                              imageUrl={productImageMap.get(display.productId)}
                            />
                          ))}
                        </div>
                      </ScrollArea>
                    </div>
                  )}
                </div>

                {/* Right column: Target machine items to receive */}
                <div className="flex flex-col min-h-0 min-w-0 overflow-hidden">
                  <div className="shrink-0 mb-2">
                    <Label className="text-xs text-muted-foreground">
                      From {selectedMachineCode} ({targetDisplays.length})
                    </Label>
                    <p className="text-xs text-green-600 dark:text-green-400">
                      {itemsToReceive.size > 0
                        ? `Receiving ${itemsToReceive.size} item${itemsToReceive.size !== 1 ? "s" : ""}`
                        : "Select items to receive"}
                    </p>
                  </div>

                  {isTargetDisplaysLoading ? (
                    <div className="flex-1 flex items-center justify-center">
                      <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                    </div>
                  ) : targetDisplays.length === 0 ? (
                    <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                      No items on display
                    </div>
                  ) : (
                    <div className="flex-1 min-h-0 overflow-hidden">
                      <ScrollArea className="h-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
                        <div className="space-y-2 pr-3 pb-2">
                          {targetDisplays.map((display) => (
                            <DisplayItem
                              key={display.id}
                              display={display}
                              selected={itemsToReceive.has(display.id)}
                              onToggle={() => toggleItemToReceive(display.id)}
                              direction="receive"
                              disabled={isSubmitting}
                              imageUrl={productImageMap.get(display.productId)}
                            />
                          ))}
                        </div>
                      </ScrollArea>
                    </div>
                  )}
                </div>
              </div>

              {/* Summary for machine transfer */}
              {hasSelectionsForMachine && (
                <div className="shrink-0 mt-3 pt-3 border-t">
                  <div className="flex items-center gap-4 text-sm">
                    {itemsToSend.size > 0 && (
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="secondary"
                          className="bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300"
                        >
                          {itemsToSend.size}
                        </Badge>
                        <span className="text-muted-foreground">
                          to {selectedMachineCode}
                        </span>
                      </div>
                    )}
                    {itemsToSend.size > 0 && itemsToReceive.size > 0 && (
                      <ArrowLeftRight className="h-4 w-4 text-muted-foreground" />
                    )}
                    {itemsToReceive.size > 0 && (
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="secondary"
                          className="bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300"
                        >
                          {itemsToReceive.size}
                        </Badge>
                        <span className="text-muted-foreground">
                          to {currentMachineCode}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </>
          )}

          {step === "select-products" && (
            <>
              {/* Two-column layout for product swap */}
              <div className="flex-1 min-h-0 grid grid-cols-2 gap-4 overflow-hidden">
                {/* Left column: Current display items to remove */}
                <div className="flex flex-col min-h-0 min-w-0 overflow-hidden">
                  <div className="shrink-0 mb-2">
                    <Label className="text-xs text-muted-foreground">
                      Current Display ({currentDisplays.length})
                    </Label>
                    <p className="text-xs text-orange-600 dark:text-orange-400">
                      {itemsToSend.size > 0
                        ? `Removing ${itemsToSend.size} item${itemsToSend.size !== 1 ? "s" : ""}`
                        : "Select items to remove"}
                    </p>
                  </div>

                  {currentDisplays.length === 0 ? (
                    <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                      No items on display
                    </div>
                  ) : (
                    <div className="flex-1 min-h-0 overflow-hidden">
                      <ScrollArea className="h-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
                        <div className="space-y-2 pr-3 pb-2">
                          {currentDisplays.map((display) => (
                            <DisplayItem
                              key={display.id}
                              display={display}
                              selected={itemsToSend.has(display.id)}
                              onToggle={() => toggleItemToSend(display.id)}
                              direction="send"
                              disabled={isSubmitting}
                              imageUrl={productImageMap.get(display.productId)}
                            />
                          ))}
                        </div>
                      </ScrollArea>
                    </div>
                  )}
                </div>

                {/* Right column: Products to add */}
                <div className="flex flex-col min-h-0 min-w-0 overflow-hidden">
                  <div className="shrink-0 mb-2">
                    <Label className="text-xs text-muted-foreground">
                      Select products to add ({availableProducts.length})
                    </Label>
                    <p className="text-xs text-green-600 dark:text-green-400">
                      {productsToAdd.size > 0
                        ? `Adding ${productsToAdd.size} product${productsToAdd.size !== 1 ? "s" : ""}`
                        : "Select products to add"}
                    </p>
                  </div>

                  <Input
                    type="text"
                    placeholder="Search products..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="h-9 mb-2 shrink-0"
                  />

                  {availableProducts.length === 0 ? (
                    <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                      {searchQuery
                        ? "No products match your search"
                        : "No products available"}
                    </div>
                  ) : (
                    <div className="flex-1 min-h-0 overflow-hidden">
                      <ScrollArea className="h-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
                        <div className="space-y-2 pr-3 pb-2">
                          {availableProducts.map((product) => (
                            <ProductItem
                              key={product.id}
                              product={product}
                              selected={productsToAdd.has(product.id)}
                              onToggle={() => toggleProductToAdd(product.id)}
                              disabled={isSubmitting}
                            />
                          ))}
                        </div>
                      </ScrollArea>
                    </div>
                  )}
                </div>
              </div>

              {/* Summary for product swap */}
              {hasSelectionsForProducts && (
                <div className="shrink-0 mt-3 pt-3 border-t">
                  <div className="flex items-center gap-4 text-sm">
                    {itemsToSend.size > 0 && (
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="secondary"
                          className="bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300"
                        >
                          {itemsToSend.size}
                        </Badge>
                        <span className="text-muted-foreground">removing</span>
                      </div>
                    )}
                    {itemsToSend.size > 0 && productsToAdd.size > 0 && (
                      <ArrowLeftRight className="h-4 w-4 text-muted-foreground" />
                    )}
                    {productsToAdd.size > 0 && (
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="secondary"
                          className="bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300"
                        >
                          {productsToAdd.size}
                        </Badge>
                        <span className="text-muted-foreground">adding</span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <DialogFooter className="shrink-0 border-t p-6">
          {step !== "select-mode" && (
            <Button
              type="button"
              variant="ghost"
              onClick={handleBack}
              disabled={isSubmitting}
              className="mr-auto"
            >
              Back
            </Button>
          )}
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          {(step === "select-items" || step === "select-products") && (
            <Button type="button" onClick={handleTransfer} disabled={!canTransfer}>
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {mode === "machine" ? "Transferring..." : "Swapping..."}
                </>
              ) : mode === "machine" ? (
                "Transfer"
              ) : (
                "Swap"
              )}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
