"use client";

import { useMemo, useState, useEffect } from "react";
import Image from "next/image";
import { format } from "date-fns";
import {
  ImageOff,
  Pencil,
  Plus,
  Trash2,
  Check,
  X,
  History,
  ChevronLeft,
  ChevronRight,
  Monitor,
} from "lucide-react";
import { Card } from "@/components/ui/card";
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
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { LocationType } from "@/types/api";
import type {
  StorageLocation,
  Inventory,
  InventoryRequest,
  BoxBin,
  Rack,
  Cabinet,
  SingleClawMachine,
  DoubleClawMachine,
  KeychainMachine,
  FourCornerMachine,
  PusherMachine,
  MachineDisplay,
  SetMachineDisplayBatchRequest,
} from "@/types/api";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import {
  useCreateInventoryMutation,
  useUpdateInventoryMutation,
  useDeleteLocationMutation,
} from "@/hooks/mutations/use-location-mutations";
import { AddInventoryDialog } from "@/components/locations/add-inventory-dialog";
import { Can } from "@/components/rbac/can";
import { Permission } from "@/lib/rbac/permissions";
import {
  useActiveDisplaysForMachine,
  useMachineDisplayHistoryPaged,
} from "@/hooks/queries/use-machine-displays";
import {
  useSetMachineDisplayBatchMutation,
  useClearDisplayByIdMutation,
} from "@/hooks/mutations/use-machine-display-mutations";
import { useProducts } from "@/hooks/queries/use-products";
import { useAuth } from "@/hooks/use-auth";
import { useToast } from "@/hooks/use-toast";

const MACHINE_LOCATION_TYPES: LocationType[] = [
  LocationType.SINGLE_CLAW_MACHINE,
  LocationType.DOUBLE_CLAW_MACHINE,
  LocationType.KEYCHAIN_MACHINE,
  LocationType.FOUR_CORNER_MACHINE,
  LocationType.PUSHER_MACHINE,
];

const HISTORY_PAGE_SIZE = 5;

function getLocationCode(locationType: LocationType, loc: StorageLocation): string {
  switch (locationType) {
    case "BOX_BIN":
      return (loc as BoxBin).boxBinCode;
    case "RACK":
      return (loc as Rack).rackCode;
    case "CABINET":
      return (loc as Cabinet).cabinetCode;
    case "SINGLE_CLAW_MACHINE":
      return (loc as SingleClawMachine).singleClawMachineCode;
    case "DOUBLE_CLAW_MACHINE":
      return (loc as DoubleClawMachine).doubleClawMachineCode;
    case "KEYCHAIN_MACHINE":
      return (loc as KeychainMachine).keychainMachineCode;
    case "FOUR_CORNER_MACHINE":
      return (loc as FourCornerMachine).fourCornerMachineCode;
    case "PUSHER_MACHINE":
      return (loc as PusherMachine).pusherMachineCode;
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

  const locationId = location?.id;
  const isMachine = MACHINE_LOCATION_TYPES.includes(locationType);

  const inventoryQuery = useLocationInventory(locationType, locationId);
  const createInventory = useCreateInventoryMutation(locationType, locationId ?? "");
  const updateInventory = useUpdateInventoryMutation(locationType, locationId ?? "");
  const deleteLocation = useDeleteLocationMutation(locationType);

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

  const historyItems = historyData?.content?.filter((item) => item.endedAt) ?? [];
  const totalHistoryPages = historyData?.totalPages ?? 0;
  const totalHistoryElements = historyData?.totalElements ?? 0;

  const hasDisplay =
    activeDisplaysForMachine.length > 0 || totalHistoryElements > 0;

  // Products for the add-product popover
  const { data: products = [] } = useProducts();

  const setDisplayBatchMutation = useSetMachineDisplayBatchMutation();
  const clearDisplayMutation = useClearDisplayByIdMutation();

  const [addOpen, setAddOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("products");
  const [productPopoverOpen, setProductPopoverOpen] = useState(false);
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [clearDialogOpen, setClearDialogOpen] = useState(false);
  const [productToClear, setProductToClear] = useState<MachineDisplay | null>(null);

  // Reset state when dialog opens / location changes
  useEffect(() => {
    if (open) {
      setActiveTab("products");
      setHistoryPage(0);
      setSelectedProductIds([]);
    }
  }, [open, locationId]);

  const code = location ? getLocationCode(locationType, location) : "";

  const totalQty = useMemo(() => {
    const inv = (inventoryQuery.data ?? []) as Inventory[];
    return inv.reduce((sum, r) => sum + (r.quantity ?? 0), 0);
  }, [inventoryQuery.data]);

  async function handleAddInventory(payload: InventoryRequest, isUpdate: boolean, inventoryId?: string) {
    if (isUpdate && inventoryId) {
      await updateInventory.mutateAsync({ inventoryId, payload });
    } else {
      await createInventory.mutateAsync(payload);
    }
  }

  // Available products not already on display
  const activeProductIds = activeDisplaysForMachine.map((d) => d.productId);
  const availableProducts = products.filter((p) => !activeProductIds.includes(p.id));

  const selectedProducts = products.filter((p) => selectedProductIds.includes(p.id));

  function handleProductSelect(productId: string) {
    setSelectedProductIds((prev) =>
      prev.includes(productId) ? prev.filter((id) => id !== productId) : [...prev, productId]
    );
  }

  async function handleAddDisplayProducts() {
    if (!locationId || selectedProductIds.length === 0) return;
    try {
      await setDisplayBatchMutation.mutateAsync({
        locationType,
        machineId: locationId,
        productIds: selectedProductIds,
        actorId: user?.personId,
      } as SetMachineDisplayBatchRequest);
      toast({
        title: "Display updated",
        description: `${selectedProductIds.length} product(s) added to display.`,
      });
      setSelectedProductIds([]);
      setProductPopoverOpen(false);
    } catch {
      toast({
        title: "Error",
        description: "Failed to update display.",
        variant: "destructive",
      });
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

  // ── Products tab content ──────────────────────────────────────────────────

  const productsTabContent = (
    <div className="space-y-4">
      {location && (
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setAddOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Add Inventory
          </Button>
          <Can permission={Permission.STORAGE_UPDATE}>
            <Button variant="outline" size="sm" onClick={() => onEdit(location)}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </Button>
          </Can>
          <Can permission={Permission.STORAGE_DELETE}>
            <Button
              variant="outline"
              size="sm"
              className="text-destructive"
              onClick={() => setDeleteOpen(true)}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          </Can>
        </div>
      )}

      {inventoryQuery.isError ? (
        <Card className="p-4 text-sm text-muted-foreground">
          Failed to load inventory.
        </Card>
      ) : null}

      <Card className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>SKU</TableHead>
              <TableHead>Product</TableHead>
              <TableHead>Category</TableHead>
              <TableHead className="text-right">Qty</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {inventoryQuery.isLoading ? (
              Array.from({ length: 6 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-48" /></TableCell>
                  <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                  <TableCell className="text-right"><Skeleton className="ml-auto h-4 w-10" /></TableCell>
                </TableRow>
              ))
            ) : (inventoryQuery.data as Inventory[] | undefined)?.length ? (
              (inventoryQuery.data as Inventory[]).map((inv) => (
                <TableRow key={inv.id}>
                  <TableCell className="py-2">
                    <div className="flex items-center gap-3">
                      {inv.item.imageUrl ? (
                        <div className="relative h-8 w-8 shrink-0 overflow-hidden rounded-md bg-muted">
                          <Image
                            src={inv.item.imageUrl}
                            alt={inv.item.name}
                            fill
                            sizes="32px"
                            className="object-cover"
                          />
                        </div>
                      ) : (
                        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-muted">
                          <ImageOff className="h-3.5 w-3.5 text-muted-foreground" />
                        </div>
                      )}
                      <span className="font-mono text-sm">{inv.item.sku}</span>
                    </div>
                  </TableCell>
                  <TableCell className="font-medium">{inv.item.name}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className="text-xs">
                      {inv.item.category.name}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">{inv.quantity}</TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={4} className="py-8 text-center text-sm text-muted-foreground">
                  No inventory in this location yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Card>
    </div>
  );

  // ── Display tab content ───────────────────────────────────────────────────

  const addDisplayEmptyState = (
    <div className="flex flex-col items-center justify-center py-12 gap-3 text-center">
      <div className="rounded-full bg-muted p-3">
        <Monitor className="h-6 w-6 text-muted-foreground" />
      </div>
      <div>
        <p className="text-sm font-medium">No display configured</p>
        <p className="text-xs text-muted-foreground mt-1">
          Set up a display to start tracking products on this machine.
        </p>
      </div>
      <Popover open={productPopoverOpen} onOpenChange={setProductPopoverOpen}>
        <PopoverTrigger asChild>
          <Button size="sm">
            <Plus className="h-4 w-4 mr-1" />
            Add Display
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[300px] p-0" align="center">
          <Command>
            <CommandInput placeholder="Search products..." />
            <CommandList>
              <CommandEmpty>No products available.</CommandEmpty>
              <CommandGroup>
                {availableProducts.map((product) => (
                  <CommandItem
                    key={product.id}
                    value={`${product.name} ${product.sku}`}
                    onSelect={() => handleProductSelect(product.id)}
                  >
                    <Check
                      className={cn(
                        "mr-2 h-4 w-4",
                        selectedProductIds.includes(product.id) ? "opacity-100" : "opacity-0"
                      )}
                    />
                    <div className="flex flex-col">
                      <span>{product.name}</span>
                      <span className="text-xs text-muted-foreground">{product.sku}</span>
                    </div>
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
          {selectedProductIds.length > 0 && (
            <div className="p-2 border-t">
              <div className="flex flex-wrap gap-1 mb-2">
                {selectedProducts.map((p) => (
                  <Badge key={p.id} variant="secondary" className="text-xs">
                    {p.name}
                    <button
                      onClick={() => handleProductSelect(p.id)}
                      className="ml-1 hover:text-destructive"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))}
              </div>
              <Button
                size="sm"
                className="w-full"
                onClick={handleAddDisplayProducts}
                disabled={setDisplayBatchMutation.isPending}
              >
                {setDisplayBatchMutation.isPending
                  ? "Adding..."
                  : `Add ${selectedProductIds.length} Product(s)`}
              </Button>
            </div>
          )}
        </PopoverContent>
      </Popover>
    </div>
  );

  const displayTabContent = !hasDisplay ? addDisplayEmptyState : (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium">Current Products</p>
        <Popover open={productPopoverOpen} onOpenChange={setProductPopoverOpen}>
          <PopoverTrigger asChild>
            <Button size="sm" variant="outline">
              <Plus className="h-4 w-4 mr-1" />
              Add Product
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-[300px] p-0" align="end">
            <Command>
              <CommandInput placeholder="Search products..." />
              <CommandList>
                <CommandEmpty>No products available.</CommandEmpty>
                <CommandGroup>
                  {availableProducts.map((product) => (
                    <CommandItem
                      key={product.id}
                      value={`${product.name} ${product.sku}`}
                      onSelect={() => handleProductSelect(product.id)}
                    >
                      <Check
                        className={cn(
                          "mr-2 h-4 w-4",
                          selectedProductIds.includes(product.id) ? "opacity-100" : "opacity-0"
                        )}
                      />
                      <div className="flex flex-col">
                        <span>{product.name}</span>
                        <span className="text-xs text-muted-foreground">{product.sku}</span>
                      </div>
                    </CommandItem>
                  ))}
                </CommandGroup>
              </CommandList>
            </Command>
            {selectedProductIds.length > 0 && (
              <div className="p-2 border-t">
                <div className="flex flex-wrap gap-1 mb-2">
                  {selectedProducts.map((p) => (
                    <Badge key={p.id} variant="secondary" className="text-xs">
                      {p.name}
                      <button
                        onClick={() => handleProductSelect(p.id)}
                        className="ml-1 hover:text-destructive"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </Badge>
                  ))}
                </div>
                <Button
                  size="sm"
                  className="w-full"
                  onClick={handleAddDisplayProducts}
                  disabled={setDisplayBatchMutation.isPending}
                >
                  {setDisplayBatchMutation.isPending
                    ? "Adding..."
                    : `Add ${selectedProductIds.length} Product(s)`}
                </Button>
              </div>
            )}
          </PopoverContent>
        </Popover>
      </div>

      {activeDisplaysForMachine.length === 0 ? (
        <div className="text-sm text-muted-foreground text-center py-6 border rounded-lg">
          No products currently displayed
        </div>
      ) : (
        <div className="space-y-2">
          {activeDisplaysForMachine.map((item) => (
            <div
              key={item.id}
              className={cn(
                "flex items-center justify-between p-3 border rounded-lg",
                item.stale && "border-amber-300 bg-amber-50 dark:bg-amber-950/20"
              )}
            >
              <div className="flex-1">
                <div className="font-medium text-sm">{item.productName}</div>
                <div className="text-xs text-muted-foreground">
                  {item.productSku} · {item.daysActive} days
                </div>
              </div>
              <div className="flex items-center gap-2">
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
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );

  // ── History tab content ───────────────────────────────────────────────────

  const historyTabContent = !hasDisplay ? (
    <div className="flex flex-col items-center justify-center py-12 gap-3 text-center">
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
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium flex items-center gap-2">
          <History className="h-4 w-4" />
          Display History
          {totalHistoryElements > 0 && (
            <span className="text-muted-foreground font-normal">
              ({totalHistoryElements})
            </span>
          )}
        </h3>
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

      <ScrollArea className="max-h-72">
        {isHistoryLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="border rounded-lg p-3 space-y-1">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-3 w-24" />
              </div>
            ))}
          </div>
        ) : historyItems.length === 0 ? (
          <div className="text-sm text-muted-foreground text-center py-6">
            No display history yet.
          </div>
        ) : (
          <div className="space-y-2 pr-2">
            {historyItems.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3 border rounded-lg text-sm"
              >
                <div>
                  <div className="font-medium">{item.productName}</div>
                  <div className="text-xs text-muted-foreground">
                    {format(new Date(item.startedAt), "MMM d")} –{" "}
                    {format(new Date(item.endedAt!), "MMM d, yyyy")}
                  </div>
                </div>
                <Badge variant="outline" className="text-xs">
                  {formatDuration(item.startedAt, item.endedAt)}
                </Badge>
              </div>
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  );

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-2xl h-[90vh] max-h-[90vh] flex flex-col overflow-hidden p-0">
          <DialogHeader className="shrink-0 px-6 pt-6 pb-4">
            <DialogTitle className="flex items-center gap-3">
              <span className="text-xl font-semibold">{code || "Location"}</span>
            </DialogTitle>
            <DialogDescription>
              {locationType} • {totalQty} total units
            </DialogDescription>
          </DialogHeader>

          <div className="flex-1 min-h-0 overflow-y-auto px-6 pb-6">
            {isMachine ? (
              <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
                <TabsList className="w-full mb-4">
                  <TabsTrigger value="products" className="flex-1">
                    Products
                  </TabsTrigger>
                  <TabsTrigger value="display" className="flex-1">
                    {!hasDisplay ? "+ Add Display" : "Display"}
                  </TabsTrigger>
                  <TabsTrigger value="history" className="flex-1">
                    History
                  </TabsTrigger>
                </TabsList>
                <TabsContent value="products">{productsTabContent}</TabsContent>
                <TabsContent value="display">{displayTabContent}</TabsContent>
                <TabsContent value="history">{historyTabContent}</TabsContent>
              </Tabs>
            ) : (
              <div className="space-y-4">{productsTabContent}</div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {locationId ? (
        <AddInventoryDialog
          open={addOpen}
          onOpenChange={setAddOpen}
          locationType={locationType}
          locationId={locationId}
          existingInventory={(inventoryQuery.data as Inventory[]) ?? []}
          isSaving={createInventory.isPending || updateInventory.isPending}
          onSubmit={handleAddInventory}
        />
      ) : null}

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {totalQty > 0 ? "Cannot Delete Location" : "Delete location?"}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {totalQty > 0 ? (
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
            {totalQty === 0 && (
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
    </>
  );
}
