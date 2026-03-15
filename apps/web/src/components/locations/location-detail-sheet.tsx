"use client";

import { useMemo, useState, useEffect } from "react";
import Image from "next/image";
import { format } from "date-fns";
import {
  ImageOff,
  Pencil,
  Plus,
  Trash2,
  History,
  ChevronLeft,
  ChevronRight,
  Monitor,
  ArrowLeftRight,
  ArrowUpDown,
  RefreshCw,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { cn } from "@/lib/utils";
import { LocationType, DISPLAY_ONLY_LOCATION_TYPES } from "@/types/api";
import type {
  StorageLocation,
  Inventory,
  BoxBin,
  Rack,
  Cabinet,
  SingleClawMachine,
  DoubleClawMachine,
  KeychainMachine,
  FourCornerMachine,
  PusherMachine,
  Window as WindowLocation,
  Gachapon,
  MachineDisplay,
  SetMachineDisplayBatchRequest,
} from "@/types/api";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import {
  useDeleteLocationMutation,
} from "@/hooks/mutations/use-location-mutations";
import { Can } from "@/components/rbac/can";
import { Permission } from "@/lib/rbac/permissions";
import {
  useActiveDisplaysForMachine,
  useMachineDisplayHistoryPaged,
} from "@/hooks/queries/use-machine-displays";
import {
  useSetMachineDisplayBatchMutation,
  useClearDisplayByIdMutation,
  useBatchSwapDisplayMutation,
  useRenewDisplayMutation,
  useDeleteDisplayHistoryMutation,
} from "@/hooks/mutations/use-machine-display-mutations";
import { AddDisplayDialog } from "@/components/machine-displays/add-display-dialog";
import { TransferDisplayDialog } from "@/components/machine-displays/transfer-display-dialog";
import { RenewDisplayDialog } from "@/components/machine-displays/renew-display-dialog";
import { AdjustStockDialog } from "@/components/stock/adjust-stock-dialog";
import { TransferStockDialog } from "@/components/stock/transfer-stock-dialog";
import { useProducts } from "@/hooks/queries/use-products";
import { useAuth } from "@/hooks/use-auth";
import { useToast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { type LocationSelection } from "@/types/transfer";

const MACHINE_LOCATION_TYPES: LocationType[] = [
  LocationType.DOUBLE_CLAW_MACHINE,
  LocationType.FOUR_CORNER_MACHINE,
  LocationType.GACHAPON,
  LocationType.KEYCHAIN_MACHINE,
  LocationType.PUSHER_MACHINE,
  LocationType.SINGLE_CLAW_MACHINE,
];

const HISTORY_PAGE_SIZE = 5;

function getLocationCode(locationType: LocationType, loc: StorageLocation): string {
  switch (locationType) {
    case "BOX_BIN":
      return (loc as BoxBin).boxBinCode;
    case "CABINET":
      return (loc as Cabinet).cabinetCode;
    case "DOUBLE_CLAW_MACHINE":
      return (loc as DoubleClawMachine).doubleClawMachineCode;
    case "FOUR_CORNER_MACHINE":
      return (loc as FourCornerMachine).fourCornerMachineCode;
    case "GACHAPON":
      return (loc as Gachapon).gachaponCode;
    case "KEYCHAIN_MACHINE":
      return (loc as KeychainMachine).keychainMachineCode;
    case "PUSHER_MACHINE":
      return (loc as PusherMachine).pusherMachineCode;
    case "RACK":
      return (loc as Rack).rackCode;
    case "SINGLE_CLAW_MACHINE":
      return (loc as SingleClawMachine).singleClawMachineCode;
    case "WINDOW":
      return (loc as WindowLocation).windowCode;
    default:
      return loc.id;
  }
}

function formatDuration(startedAt: string, endedAt: string | null): string {
  const start = new Date(startedAt);
  const end = endedAt ? new Date(endedAt) : new Date();
  const days = Math.floor(
    (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)
  );
  if (days === 0) return "< 1 day";
  if (days === 1) return "1 day";
  return `${days} days`;
}

interface LocationDetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  locationType: LocationType;
  location: StorageLocation | null;
  onEdit: (location: StorageLocation) => void;
}

export function LocationDetailSheet({
  open,
  onOpenChange,
  locationType,
  location,
  onEdit,
}: LocationDetailSheetProps) {
  const { user } = useAuth();
  const { toast } = useToast();
  const { isAdmin } = usePermissions();

  const locationId = location?.id;
  const isMachine = MACHINE_LOCATION_TYPES.includes(locationType);
  const isDisplayOnly = DISPLAY_ONLY_LOCATION_TYPES.includes(locationType);

  const inventoryQuery = useLocationInventory(locationType, locationId);
  const deleteLocation = useDeleteLocationMutation(locationType);

  const [adjustOpen, setAdjustOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);

  const currentLocationSelection: LocationSelection | null = useMemo(() => {
    if (!location || !locationId) return null;
    const locationCode = getLocationCode(locationType, location);
    return {
      locationType,
      locationId,
      locationCode,
    };
  }, [location, locationId, locationType]);

  // Machine display queries (only active for machine types)
  const { data: activeDisplaysForMachine = [] } = useActiveDisplaysForMachine(
    isMachine ? locationType : undefined,
    isMachine ? locationId : undefined
  );
  const [historyPage, setHistoryPage] = useState(0);
  const { data: historyData, isLoading: isHistoryLoading } = useMachineDisplayHistoryPaged(
    isMachine ? locationType : undefined,
    isMachine ? locationId : undefined,
    historyPage,
    HISTORY_PAGE_SIZE
  );

  const historyItems = historyData?.content ?? [];
  const totalHistoryPages = historyData?.totalPages ?? 0;
  const totalHistoryElements = historyData?.totalElements ?? 0;

  const hasDisplay =
    activeDisplaysForMachine.length > 0 || totalHistoryElements > 0;

  // Products for the add-product popover
  const { data: products = [] } = useProducts();

  const setDisplayBatchMutation = useSetMachineDisplayBatchMutation();
  const clearDisplayMutation = useClearDisplayByIdMutation();
  const batchSwapMutation = useBatchSwapDisplayMutation();
  const renewDisplayMutation = useRenewDisplayMutation();
  const deleteHistoryMutation = useDeleteDisplayHistoryMutation();

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("products");
  const [addDisplayDialogOpen, setAddDisplayDialogOpen] = useState(false);
  const [transferDisplayDialogOpen, setTransferDisplayDialogOpen] = useState(false);
  const [renewDisplayDialogOpen, setRenewDisplayDialogOpen] = useState(false);
  const [isTransferring, setIsTransferring] = useState(false);
  const [clearDialogOpen, setClearDialogOpen] = useState(false);
  const [productToClear, setProductToClear] = useState<MachineDisplay | null>(null);
  const [historyDeleteDialogOpen, setHistoryDeleteDialogOpen] = useState(false);
  const [historyToDelete, setHistoryToDelete] = useState<MachineDisplay | null>(null);

  // Reset state when dialog opens / location changes
  useEffect(() => {
    if (open) {
      // Display-only locations default to display tab (no products tab)
      setActiveTab(isDisplayOnly ? "display" : "products");
      setHistoryPage(0);
    }
  }, [open, locationId, isDisplayOnly]);

  const code = location ? getLocationCode(locationType, location) : "";

  const inventory = (inventoryQuery.data ?? []) as Inventory[];

  const totalQty = useMemo(() => {
    return inventory.reduce((sum, r) => sum + (r.quantity ?? 0), 0);
  }, [inventory]);

  async function handleAddDisplayProducts(productIds: string[]) {
    if (!locationId || productIds.length === 0) return;
    try {
      await setDisplayBatchMutation.mutateAsync({
        locationType,
        machineId: locationId,
        productIds,
        actorId: user?.personId,
      } as SetMachineDisplayBatchRequest);
      toast({
        title: "Display updated",
        description: `${productIds.length} product(s) added to display.`,
      });
      setAddDisplayDialogOpen(false);
    } catch {
      toast({
        title: "Error",
        description: "Failed to update display.",
        variant: "destructive",
      });
    }
  }

  async function handleTransferDisplays(
    itemsToSend: MachineDisplay[],
    itemsToReceive: MachineDisplay[],
    targetMachineId: string
  ) {
    if (!locationId) return;

    setIsTransferring(true);
    try {
      // Use the batch swap endpoint for a single transaction and single audit log
      await batchSwapMutation.mutateAsync({
        locationType,
        machineId: locationId,
        targetLocationType: locationType,
        targetMachineId,
        displayIdsToTarget: itemsToSend.map((item) => item.id),
        displayIdsFromTarget: itemsToReceive.map((item) => item.id),
        actorId: user?.personId,
      });

      const sentCount = itemsToSend.length;
      const receivedCount = itemsToReceive.length;
      let description = "";
      if (sentCount > 0 && receivedCount > 0) {
        description = `Sent ${sentCount} and received ${receivedCount} product(s).`;
      } else if (sentCount > 0) {
        description = `Sent ${sentCount} product(s).`;
      } else {
        description = `Received ${receivedCount} product(s).`;
      }

      toast({
        title: "Transfer complete",
        description,
      });
      setTransferDisplayDialogOpen(false);
    } catch {
      toast({
        title: "Error",
        description: "Failed to transfer displays.",
        variant: "destructive",
      });
    } finally {
      setIsTransferring(false);
    }
  }

  function handleClearClick(item: MachineDisplay) {
    setProductToClear(item);
    setClearDialogOpen(true);
  }

  async function handleConfirmClear() {
    if (!productToClear) return;
    try {
      await clearDisplayMutation.mutateAsync({
        displayId: productToClear.id,
        actorId: user?.personId,
      });
      toast({
        title: "Product removed",
        description: `"${productToClear.productName}" removed from display.`,
      });
    } catch {
      toast({
        title: "Error",
        description: "Failed to remove product from display.",
        variant: "destructive",
      });
    }
    setClearDialogOpen(false);
    setProductToClear(null);
  }

  async function handleSwapWithProducts(
    itemsToRemove: MachineDisplay[],
    productsToAdd: string[]
  ) {
    if (!locationId) return;

    setIsTransferring(true);
    try {
      // Use the batch swap endpoint for a single transaction and single audit log
      await batchSwapMutation.mutateAsync({
        locationType,
        machineId: locationId,
        displayIdsToRemove: itemsToRemove.map((item) => item.id),
        productIdsToAdd: productsToAdd,
        actorId: user?.personId,
      });

      const removedCount = itemsToRemove.length;
      const addedCount = productsToAdd.length;
      let description = "";
      if (removedCount > 0 && addedCount > 0) {
        description = `Removed ${removedCount} and added ${addedCount} product(s).`;
      } else if (removedCount > 0) {
        description = `Removed ${removedCount} product(s) from display.`;
      } else {
        description = `Added ${addedCount} product(s) to display.`;
      }

      toast({
        title: "Swap complete",
        description,
      });
      setTransferDisplayDialogOpen(false);
    } catch {
      toast({
        title: "Error",
        description: "Failed to swap display.",
        variant: "destructive",
      });
    } finally {
      setIsTransferring(false);
    }
  }

  async function handleRenewDisplays(displayIds: string[]) {
    if (!locationId || displayIds.length === 0) return;
    try {
      await renewDisplayMutation.mutateAsync({
        locationType,
        machineId: locationId,
        displayIds,
        actorId: user?.personId,
      });
      toast({
        title: "Displays renewed",
        description: `${displayIds.length} display(s) have been renewed.`,
      });
      setRenewDisplayDialogOpen(false);
    } catch {
      toast({
        title: "Error",
        description: "Failed to renew displays.",
        variant: "destructive",
      });
    }
  }

  function handleDeleteHistoryClick(item: MachineDisplay) {
    setHistoryToDelete(item);
    setHistoryDeleteDialogOpen(true);
  }

  async function handleConfirmDeleteHistory() {
    if (!historyToDelete) return;
    try {
      await deleteHistoryMutation.mutateAsync(historyToDelete.id);
      toast({
        title: "History deleted",
        description: `History record for "${historyToDelete.productName}" has been deleted.`,
      });
    } catch {
      toast({
        title: "Error",
        description: "Failed to delete history record.",
        variant: "destructive",
      });
    }
    setHistoryDeleteDialogOpen(false);
    setHistoryToDelete(null);
  }

  // ── Products tab content ──────────────────────────────────────────────────

  const productsTabContent = (
    <>
      {location && (
        <div className="shrink-0 flex items-center gap-1 sm:gap-2 py-4">
          <Button variant="outline" size="sm" className="sm:flex-1 h-7 px-2 text-xs sm:h-9 sm:px-3 sm:text-sm" onClick={() => setAdjustOpen(true)}>
            <ArrowUpDown className="h-3 w-3 sm:h-4 sm:w-4 mr-1 sm:mr-2" />
            Adjust
          </Button>
          <Button variant="outline" size="sm" className="sm:flex-1 h-7 px-2 text-xs sm:h-9 sm:px-3 sm:text-sm" onClick={() => setTransferOpen(true)}>
            <RefreshCw className="h-3 w-3 sm:h-4 sm:w-4 mr-1 sm:mr-2" />
            Transfer
          </Button>
          <Can permission={Permission.STORAGE_UPDATE}>
            <Button variant="outline" size="sm" className="sm:flex-1 h-7 px-2 text-xs sm:h-9 sm:px-3 sm:text-sm" onClick={() => onEdit(location)}>
              <Pencil className="h-3 w-3 sm:h-4 sm:w-4 mr-1 sm:mr-2" />
              Edit
            </Button>
          </Can>
          <Can permission={Permission.STORAGE_DELETE}>
            <Button
              variant="outline"
              size="sm"
              className="sm:flex-1 h-7 px-2 text-xs sm:h-9 sm:px-3 sm:text-sm text-destructive"
              onClick={() => setDeleteOpen(true)}
            >
              <Trash2 className="h-3 w-3 sm:h-4 sm:w-4 mr-1 sm:mr-2" />
              Delete
            </Button>
          </Can>
        </div>
      )}

      {inventoryQuery.isError ? (
        <p className="shrink-0 text-sm text-muted-foreground pb-2">Failed to load inventory.</p>
      ) : null}

      <div className="flex-1 min-h-0">
        <ScrollArea className="h-full **:data-[slot=scroll-area-viewport]:overscroll-auto">
          <div className="pb-6 pr-3">
            {inventoryQuery.isLoading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3 py-3 sm:py-4 border-b last:border-b-0">
                  <Skeleton className="h-12 w-12 sm:h-20 sm:w-20 rounded-lg shrink-0" />
                  <div className="flex-1 space-y-1.5">
                    <Skeleton className="h-4 w-40" />
                    <Skeleton className="h-3.5 w-20" />
                  </div>
                  <Skeleton className="h-5 w-8" />
                </div>
              ))
            ) : inventory.length ? (
              inventory.map((inv) => (
                <div key={inv.id} className="flex items-center gap-2 sm:gap-4 py-3 sm:py-4 border-b last:border-b-0">
                  <div className="relative h-12 w-12 sm:h-20 sm:w-20 flex-shrink-0 rounded-lg overflow-hidden bg-muted">
                    {inv.item.imageUrl ? (
                      <Image
                        src={inv.item.imageUrl}
                        alt={inv.item.name}
                        fill
                        sizes="(max-width: 640px) 48px, 80px"
                        className="object-cover"
                      />
                    ) : (
                      <div className="h-full w-full flex items-center justify-center">
                        <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
                      </div>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-xs sm:text-base truncate">{inv.item.name}</p>
                    <div className="flex flex-wrap gap-1 mt-1">
                      <Badge variant="secondary" className="text-[10px] sm:text-xs px-1.5 py-0">
                        {inv.item.category.name}
                      </Badge>
                      <span className="font-mono text-[10px] sm:text-xs text-muted-foreground">{inv.item.sku}</span>
                    </div>
                  </div>
                  <div className="flex flex-col items-center gap-0.5 shrink-0">
                    <span className="text-sm font-semibold tabular-nums">{inv.quantity}</span>
                    <span className="text-[10px] sm:text-xs text-muted-foreground">in stock</span>
                  </div>
                </div>
              ))
            ) : (
              <div className="flex items-center justify-center py-8 text-center text-sm text-muted-foreground">
                No inventory in this location yet
              </div>
            )}
          </div>
        </ScrollArea>
      </div>
    </>
  );

  // ── Display tab content ───────────────────────────────────────────────────

  const addDisplayEmptyState = (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 text-center">
      <div className="rounded-full bg-muted p-3">
        <Monitor className="h-6 w-6 text-muted-foreground" />
      </div>
      <div>
        <p className="text-sm font-medium">No display configured</p>
        <p className="text-xs text-muted-foreground mt-1">
          Set up a display to start tracking products on this machine.
        </p>
      </div>
      <Button size="sm" onClick={() => setAddDisplayDialogOpen(true)}>
        <Plus className="h-4 w-4 mr-1" />
        Add Display
      </Button>
    </div>
  );

  const displayTabContent = !hasDisplay ? addDisplayEmptyState : (
    <>
      <div className="shrink-0 flex items-center justify-between py-4 min-h-[52px]">
        <p className="text-sm font-medium">Current Products</p>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" onClick={() => setAddDisplayDialogOpen(true)}>
            <Plus className="h-4 w-4 mr-1" />
            Add Product
          </Button>
          <Button size="sm" variant="outline" onClick={() => setTransferDisplayDialogOpen(true)}>
            <ArrowLeftRight className="h-4 w-4 mr-1" />
            Transfer
          </Button>
          <Button size="sm" variant="outline" onClick={() => setRenewDisplayDialogOpen(true)}>
            <RefreshCw className="h-4 w-4 mr-1" />
            Renew
          </Button>
        </div>
      </div>

      <div className="flex-1 min-h-0">
        <ScrollArea className="h-full **:data-[slot=scroll-area-viewport]:overscroll-auto">
          <div className="pb-6 pr-3">
            {activeDisplaysForMachine.length === 0 ? (
              <div className="flex items-center justify-center py-8 text-center text-sm text-muted-foreground">
                No products currently displayed
              </div>
            ) : (
              activeDisplaysForMachine.map((item) => {
                const product = products.find((p) => p.id === item.productId);
                return (
                  <div
                    key={item.id}
                    className={cn(
                      "flex items-center gap-2 sm:gap-4 py-3 sm:py-4 border-b last:border-b-0",
                      item.stale && "bg-amber-50 dark:bg-amber-950/20"
                    )}
                  >
                    <div className="relative h-12 w-12 sm:h-20 sm:w-20 flex-shrink-0 rounded-lg overflow-hidden bg-muted">
                      {product?.imageUrl ? (
                        <Image
                          src={product.imageUrl}
                          alt={item.productName}
                          fill
                          sizes="(max-width: 640px) 48px, 80px"
                          className="object-cover"
                        />
                      ) : (
                        <div className="h-full w-full flex items-center justify-center">
                          <Monitor className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
                        </div>
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-medium text-xs sm:text-base truncate">{item.productName}</p>
                      <div className="flex flex-wrap gap-1 mt-1">
                        <span className="font-mono text-[10px] sm:text-xs text-muted-foreground">{item.productSku}</span>
                        <span className="text-[10px] sm:text-xs text-muted-foreground">· {item.daysActive} days</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {item.stale && (
                        <Badge variant="secondary" className="text-amber-600 text-xs">
                          Stale
                        </Badge>
                      )}
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                        onClick={() => handleClearClick(item)}
                        title="Remove product"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </ScrollArea>
      </div>
    </>
  );

  // ── History tab content ───────────────────────────────────────────────────

  const historyTabContent = !hasDisplay ? (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 text-center">
      <div className="rounded-full bg-muted p-3">
        <History className="h-6 w-6 text-muted-foreground" />
      </div>
      <div>
        <p className="text-sm font-medium">No display history</p>
        <p className="text-xs text-muted-foreground mt-1">
          History will appear here once a display has been configured.
        </p>
      </div>
      <Button size="sm" onClick={() => setActiveTab("display")}>
        <Plus className="h-4 w-4 mr-1" />
        Add Display
      </Button>
    </div>
  ) : (
    <>
      <div className="shrink-0 flex items-center justify-between py-4 min-h-[52px]">
        <p className="text-sm font-medium">
          Display History{totalHistoryElements > 0 && ` (${totalHistoryElements})`}
        </p>
        {totalHistoryPages > 1 && (
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
              disabled={historyPage === 0}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-xs text-muted-foreground min-w-[3rem] text-center">
              {historyPage + 1} / {totalHistoryPages}
            </span>
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={() => setHistoryPage((p) => Math.min(totalHistoryPages - 1, p + 1))}
              disabled={historyPage >= totalHistoryPages - 1}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        )}
      </div>

      <div className="flex-1 min-h-0 overflow-hidden">
        <ScrollArea className="h-full **:data-[slot=scroll-area-viewport]:overscroll-auto">
          <div className="pb-6 pr-3 overflow-x-hidden">
            {isHistoryLoading ? (
              Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="flex items-center gap-2 sm:gap-4 py-3 sm:py-4 border-b last:border-b-0">
                  <Skeleton className="h-12 w-12 sm:h-20 sm:w-20 rounded-lg shrink-0" />
                  <div className="flex-1 space-y-1.5">
                    <Skeleton className="h-4 w-32" />
                    <Skeleton className="h-3 w-24" />
                  </div>
                  <Skeleton className="h-5 w-16 shrink-0" />
                </div>
              ))
            ) : historyItems.length === 0 ? (
              <div className="flex items-center justify-center py-8 text-center text-sm text-muted-foreground">
                No display history yet
              </div>
            ) : (
              (() => {
                const activeItems = historyItems.filter((item) => !item.endedAt);
                const pastItems = historyItems.filter((item) => item.endedAt);
                const renderItem = (item: MachineDisplay, isActive: boolean) => {
                  const product = products.find((p) => p.id === item.productId);
                  return (
                    <div
                      key={item.id}
                      className={cn(
                        "flex items-center gap-2 sm:gap-4 py-3 sm:py-4 min-w-0 overflow-hidden",
                        isActive
                          ? "bg-green-50 dark:bg-green-950/30 rounded-lg px-2 sm:px-3 my-1 ring-1 ring-inset ring-green-200 dark:ring-green-800"
                          : "border-b last:border-b-0"
                      )}
                    >
                    <div className="relative h-12 w-12 sm:h-20 sm:w-20 flex-shrink-0 rounded-lg overflow-hidden bg-muted">
                      {product?.imageUrl ? (
                        <Image
                          src={product.imageUrl}
                          alt={item.productName}
                          fill
                          sizes="(max-width: 640px) 48px, 80px"
                          className="object-cover"
                        />
                      ) : (
                        <div className="h-full w-full flex items-center justify-center">
                          <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
                        </div>
                      )}
                    </div>
                    <div className="flex-1 w-0">
                      <p className="font-medium text-xs sm:text-base truncate">{item.productName}</p>
                      <p className="text-[10px] sm:text-xs text-muted-foreground mt-1 truncate">
                        {format(new Date(item.startedAt), "MMM d")} –{" "}
                        {item.endedAt
                          ? format(new Date(item.endedAt), "MMM d, yyyy")
                          : "Present"}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {!item.endedAt && (
                        <Badge variant="default" className="text-xs bg-green-600">
                          Active
                        </Badge>
                      )}
                      <Badge variant="outline" className="text-xs">
                        {formatDuration(item.startedAt, item.endedAt)}
                      </Badge>
                      {isAdmin && item.endedAt && (
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-6 w-6 text-muted-foreground hover:text-destructive"
                          onClick={() => handleDeleteHistoryClick(item)}
                          title="Delete history"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                  );
                };
                return (
                  <>
                    {activeItems.map((item) => renderItem(item, true))}
                    {activeItems.length > 0 && pastItems.length > 0 && (
                      <div className="border-b my-3" />
                    )}
                    {pastItems.map((item) => renderItem(item, false))}
                  </>
                );
              })()
            )}
          </div>
        </ScrollArea>
      </div>
    </>
  );

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-2xl h-[90vh] max-h-[90vh] flex flex-col overflow-hidden p-0">
          <DialogHeader className="shrink-0 px-6 pt-6 pb-2">
            <DialogTitle className="flex items-center gap-3">
              <span className="text-xl font-semibold">{code || "Location"}</span>
            </DialogTitle>
            <div className="flex items-center justify-between">
              <DialogDescription className="mt-0">
                {locationType} • {isDisplayOnly ? `${activeDisplaysForMachine.length} products` : `${totalQty} total units`}
              </DialogDescription>
              {/* Edit/Delete icons for display-only types */}
              {isDisplayOnly && location && (
                <div className="flex items-center gap-1">
                  <Can permission={Permission.STORAGE_UPDATE}>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7"
                      onClick={() => onEdit(location)}
                      title="Edit location"
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                  </Can>
                  <Can permission={Permission.STORAGE_DELETE}>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-destructive hover:text-destructive"
                      onClick={() => setDeleteOpen(true)}
                      title="Delete location"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </Can>
                </div>
              )}
            </div>
          </DialogHeader>

          <div className="flex-1 min-h-0 flex flex-col overflow-hidden px-6">
            {isMachine ? (
              <Tabs value={activeTab} onValueChange={setActiveTab} className="flex flex-col flex-1 min-h-0">
                <TabsList className="w-full shrink-0">
                  {!isDisplayOnly && (
                    <TabsTrigger value="products" className="flex-1">
                      Products
                    </TabsTrigger>
                  )}
                  <TabsTrigger value="display" className="flex-1">
                    {!hasDisplay ? "+ Add Display" : "Machine Swap"}
                  </TabsTrigger>
                  <TabsTrigger value="history" className="flex-1">
                    History
                  </TabsTrigger>
                </TabsList>
                {!isDisplayOnly && (
                  <TabsContent value="products" className="flex flex-col flex-1 min-h-0 mt-0">
                    {productsTabContent}
                  </TabsContent>
                )}
                <TabsContent value="display" className="flex flex-col flex-1 min-h-0 mt-0">
                  {displayTabContent}
                </TabsContent>
                <TabsContent value="history" className="flex flex-col flex-1 min-h-0 mt-0">
                  {historyTabContent}
                </TabsContent>
              </Tabs>
            ) : (
              <div className="flex flex-col flex-1 min-h-0">{productsTabContent}</div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      <AdjustStockDialog
        open={adjustOpen}
        onOpenChange={setAdjustOpen}
        initialLocation={currentLocationSelection}
      />
      <TransferStockDialog
        open={transferOpen}
        onOpenChange={setTransferOpen}
        initialSourceLocation={currentLocationSelection}
      />

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {isDisplayOnly
                ? activeDisplaysForMachine.length > 0
                  ? "Cannot Delete Location"
                  : "Delete location?"
                : totalQty > 0
                  ? "Cannot Delete Location"
                  : "Delete location?"}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {isDisplayOnly ? (
                activeDisplaysForMachine.length > 0 ? (
                  <>
                    This location cannot be deleted because it still has{" "}
                    <span className="font-semibold">{activeDisplaysForMachine.length}</span> active
                    display(s). Please remove all products from the display before
                    deleting it.
                  </>
                ) : (
                  <>
                    This will permanently delete{" "}
                    <span className="font-medium">{code}</span>. This action
                    cannot be undone.
                  </>
                )
              ) : totalQty > 0 ? (
                <>
                  This location cannot be deleted because it still has{" "}
                  <span className="font-semibold">{totalQty}</span> units in
                  storage. Please remove all inventory from this location before
                  deleting it.
                </>
              ) : (
                <>
                  This will permanently delete{" "}
                  <span className="font-medium">{code}</span>. This action
                  cannot be undone.
                </>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            {(isDisplayOnly ? activeDisplaysForMachine.length === 0 : totalQty === 0) && (
              <AlertDialogAction
                className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                onClick={async () => {
                  if (!locationId) return;
                  await deleteLocation.mutateAsync({ id: locationId });
                  onOpenChange(false);
                }}
              >
                Delete
              </AlertDialogAction>
            )}
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={clearDialogOpen} onOpenChange={setClearDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove Product?</AlertDialogTitle>
            <AlertDialogDescription>
              This will remove &quot;{productToClear?.productName}&quot; from the display.
              The history will be preserved.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleConfirmClear}>
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={historyDeleteDialogOpen} onOpenChange={setHistoryDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete History Record?</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete this history record for{" "}
              <span className="font-medium">&quot;{historyToDelete?.productName}&quot;</span>?
              This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmDeleteHistory}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteHistoryMutation.isPending}
            >
              {deleteHistoryMutation.isPending ? "Deleting..." : "Delete"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AddDisplayDialog
        open={addDisplayDialogOpen}
        onOpenChange={setAddDisplayDialogOpen}
        activeDisplays={activeDisplaysForMachine}
        isSaving={setDisplayBatchMutation.isPending}
        onSubmit={handleAddDisplayProducts}
      />

      <TransferDisplayDialog
        open={transferDisplayDialogOpen}
        onOpenChange={setTransferDisplayDialogOpen}
        locationType={locationType}
        currentMachineId={locationId ?? ""}
        currentMachineCode={code}
        currentDisplays={activeDisplaysForMachine}
        actorId={user?.personId}
        onTransferWithMachine={handleTransferDisplays}
        onSwapWithProducts={handleSwapWithProducts}
        isSubmitting={isTransferring}
      />

      <RenewDisplayDialog
        open={renewDisplayDialogOpen}
        onOpenChange={setRenewDisplayDialogOpen}
        currentDisplays={activeDisplaysForMachine}
        products={products}
        isSaving={renewDisplayMutation.isPending}
        onSubmit={handleRenewDisplays}
      />
    </>
  );
}
