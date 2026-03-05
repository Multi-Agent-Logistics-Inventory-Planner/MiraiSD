"use client";

import { useState, useEffect } from "react";
import { format } from "date-fns";
import { History, Plus, X, Trash2, ChevronLeft, ChevronRight } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
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
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  MachineDisplay,
  Product,
  LOCATION_TYPE_LABELS,
  SetMachineDisplayBatchRequest,
} from "@/types/api";
import { useMachineDisplayHistoryPaged } from "@/hooks/queries/use-machine-displays";

interface MachineDisplayDetailModalProps {
  display: MachineDisplay | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  products: Product[];
  actorId?: string;
  onAddProducts: (data: SetMachineDisplayBatchRequest) => Promise<void>;
  onClearDisplay: (displayId: string) => Promise<void>;
  isSubmitting?: boolean;
  /** All currently active displays for this machine */
  activeDisplaysForMachine?: MachineDisplay[];
}

function HistorySkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="border rounded-lg p-3 space-y-2">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-3 w-24" />
        </div>
      ))}
    </div>
  );
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

const HISTORY_PAGE_SIZE = 5;

export function MachineDisplayDetailModal({
  display,
  open,
  onOpenChange,
  products,
  actorId,
  onAddProducts,
  onClearDisplay,
  isSubmitting = false,
  activeDisplaysForMachine = [],
}: MachineDisplayDetailModalProps) {
  const [productPopoverOpen, setProductPopoverOpen] = useState(false);
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [clearDialogOpen, setClearDialogOpen] = useState(false);
  const [productToClear, setProductToClear] = useState<MachineDisplay | null>(null);
  const [historyPage, setHistoryPage] = useState(0);

  // Reset page when display changes or modal opens
  useEffect(() => {
    if (open) {
      setHistoryPage(0);
    }
  }, [open, display?.machineId]);

  const { data: historyData, isLoading } = useMachineDisplayHistoryPaged(
    display?.locationType,
    display?.machineId,
    historyPage,
    HISTORY_PAGE_SIZE
  );

  // Filter to only show ended displays
  const historyItems = historyData?.content?.filter((item) => item.endedAt) ?? [];
  const totalPages = historyData?.totalPages ?? 0;
  const totalElements = historyData?.totalElements ?? 0;

  // Filter out products already displayed in this machine
  const activeProductIds = activeDisplaysForMachine.map((d) => d.productId);
  const availableProducts = products.filter(
    (p) => !activeProductIds.includes(p.id)
  );

  const handleProductSelect = (productId: string) => {
    setSelectedProductIds((prev) =>
      prev.includes(productId)
        ? prev.filter((id) => id !== productId)
        : [...prev, productId]
    );
  };

  const handleAddProducts = async () => {
    if (!display || selectedProductIds.length === 0) return;

    await onAddProducts({
      locationType: display.locationType,
      machineId: display.machineId,
      productIds: selectedProductIds,
      actorId,
    });
    setSelectedProductIds([]);
    setProductPopoverOpen(false);
  };

  const handleClearClick = (displayItem: MachineDisplay) => {
    setProductToClear(displayItem);
    setClearDialogOpen(true);
  };

  const handleConfirmClear = async () => {
    if (productToClear) {
      await onClearDisplay(productToClear.id);
      setClearDialogOpen(false);
      setProductToClear(null);
    }
  };

  const selectedProducts = products.filter((p) =>
    selectedProductIds.includes(p.id)
  );

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-xl max-h-[85vh] flex flex-col">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              {display?.machineCode}
              <Badge variant="outline">
                {display && LOCATION_TYPE_LABELS[display.locationType]}
              </Badge>
            </DialogTitle>
            <DialogDescription>
              Manage products displayed in this machine
            </DialogDescription>
          </DialogHeader>

          <div className="flex-1 overflow-hidden flex flex-col gap-4">
            {/* Current Products Section */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-sm font-medium">Current Products</h3>
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
                        <CommandEmpty>No products </CommandEmpty>
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
                                  selectedProductIds.includes(product.id)
                                    ? "opacity-100"
                                    : "opacity-0"
                                )}
                              />
                              <div className="flex flex-col">
                                <span>{product.name}</span>
                                <span className="text-xs text-muted-foreground">
                                  {product.sku}
                                </span>
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
                          onClick={handleAddProducts}
                          disabled={isSubmitting}
                        >
                          {isSubmitting ? "Adding..." : `Add ${selectedProductIds.length} Product(s)`}
                        </Button>
                      </div>
                    )}
                  </PopoverContent>
                </Popover>
              </div>

              {activeDisplaysForMachine.length === 0 ? (
                <div className="text-sm text-muted-foreground text-center py-4 border rounded-lg">
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
                        <div className="font-medium">{item.productName}</div>
                        <div className="text-xs text-muted-foreground">
                          {item.productSku} · {item.daysActive} days
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        {item.stale && (
                          <Badge variant="secondary" className="text-amber-600">
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

            <Separator />

            {/* History Section */}
            <div className="flex-1 overflow-hidden flex flex-col">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-sm font-medium flex items-center gap-2">
                  <History className="h-4 w-4" />
                  History
                  {totalElements > 0 && (
                    <span className="text-muted-foreground font-normal">
                      ({totalElements})
                    </span>
                  )}
                </h3>
                {/* Pagination Controls */}
                {totalPages > 1 && (
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
                      {historyPage + 1} / {totalPages}
                    </span>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7"
                      onClick={() => setHistoryPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={historyPage >= totalPages - 1}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                )}
              </div>
              <ScrollArea className="flex-1">
                {isLoading ? (
                  <HistorySkeleton />
                ) : historyItems.length === 0 ? (
                  <div className="text-sm text-muted-foreground text-center py-4">
                    No history found
                  </div>
                ) : (
                  <div className="space-y-2 pr-4">
                    {historyItems.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center justify-between p-2 border rounded-lg text-sm"
                      >
                        <div>
                          <div className="font-medium">{item.productName}</div>
                          <div className="text-xs text-muted-foreground">
                            {format(new Date(item.startedAt), "MMM d")} -{" "}
                            {format(new Date(item.endedAt!), "MMM d, yyyy")}
                          </div>
                        </div>
                        <Badge variant="outline">
                          {formatDuration(item.startedAt, item.endedAt)}
                        </Badge>
                      </div>
                    ))}
                  </div>
                )}
              </ScrollArea>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Confirm Clear Dialog */}
      <AlertDialog open={clearDialogOpen} onOpenChange={setClearDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove Product?</AlertDialogTitle>
            <AlertDialogDescription>
              This will remove "{productToClear?.productName}" from the display.
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
