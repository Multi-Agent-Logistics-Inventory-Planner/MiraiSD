"use client";

import { useState } from "react";
import { ArrowLeftRight, Check } from "lucide-react";
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
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { cn } from "@/lib/utils";
import { MachineDisplay, Product } from "@/types/api";

interface SwapDisplayDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  outgoingDisplay: MachineDisplay | null;
  products: Product[];
  /** All currently active displays for the machine (used to exclude already-displayed products) */
  activeDisplaysForMachine: MachineDisplay[];
  actorId?: string;
  onSwap: (outgoingDisplayId: string, incomingProductId: string) => Promise<void>;
  isSubmitting?: boolean;
}

export function SwapDisplayDialog({
  open,
  onOpenChange,
  outgoingDisplay,
  products,
  activeDisplaysForMachine,
  onSwap,
  isSubmitting = false,
}: SwapDisplayDialogProps) {
  const [incomingProductId, setIncomingProductId] = useState<string | null>(null);

  const activeProductIds = new Set(activeDisplaysForMachine.map((d) => d.productId));
  const availableProducts = products.filter((p) => !activeProductIds.has(p.id));
  const incomingProduct = products.find((p) => p.id === incomingProductId) ?? null;

  function handleOpenChange(next: boolean) {
    if (!next) {
      setIncomingProductId(null);
    }
    onOpenChange(next);
  }

  async function handleSwap() {
    if (!outgoingDisplay || !incomingProductId) return;
    await onSwap(outgoingDisplay.id, incomingProductId);
    setIncomingProductId(null);
  }

  const canSwap = Boolean(outgoingDisplay && incomingProductId && !isSubmitting);

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Swap Display</DialogTitle>
          <DialogDescription>
            Replace the current product with a different one.
          </DialogDescription>
        </DialogHeader>

        {/* Swap visualisation */}
        <div className="flex items-stretch gap-3">
          {/* Outgoing side */}
          <div className="flex-1 rounded-lg border bg-muted/40 p-3 space-y-1">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
              Removing
            </p>
            {outgoingDisplay ? (
              <>
                <p className="font-medium leading-tight">
                  {outgoingDisplay.productName}
                </p>
                <p className="text-xs text-muted-foreground">
                  {outgoingDisplay.productSku}
                </p>
                <div className="flex items-center gap-1.5 pt-0.5">
                  <Badge
                    variant={outgoingDisplay.stale ? "secondary" : "outline"}
                    className={cn(
                      "text-xs",
                      outgoingDisplay.stale &&
                        "border-amber-300 bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300"
                    )}
                  >
                    {outgoingDisplay.daysActive}d active
                  </Badge>
                  {outgoingDisplay.stale && (
                    <Badge
                      variant="secondary"
                      className="text-xs text-amber-600 dark:text-amber-400"
                    >
                      Stale
                    </Badge>
                  )}
                </div>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">—</p>
            )}
          </div>

          <div className="flex items-center self-center">
            <ArrowLeftRight className="h-5 w-5 shrink-0 text-muted-foreground" />
          </div>

          {/* Incoming side */}
          <div
            className={cn(
              "flex-1 rounded-lg border p-3 space-y-1 transition-colors",
              incomingProduct
                ? "bg-background border-primary/40"
                : "bg-muted/20 border-dashed"
            )}
          >
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
              Replacing with
            </p>
            {incomingProduct ? (
              <>
                <p className="font-medium leading-tight">{incomingProduct.name}</p>
                <p className="text-xs text-muted-foreground">{incomingProduct.sku}</p>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">Select below</p>
            )}
          </div>
        </div>

        {/* Product picker */}
        <div className="rounded-lg border overflow-hidden">
          <Command>
            <CommandInput placeholder="Search products..." />
            <CommandList className="max-h-52">
              <CommandEmpty>No products available.</CommandEmpty>
              <CommandGroup>
                {availableProducts.map((product) => (
                  <CommandItem
                    key={product.id}
                    value={`${product.name} ${product.sku}`}
                    onSelect={() =>
                      setIncomingProductId((prev) =>
                        prev === product.id ? null : product.id
                      )
                    }
                  >
                    <Check
                      className={cn(
                        "mr-2 h-4 w-4 shrink-0",
                        incomingProductId === product.id
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
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSwap}
            disabled={!canSwap}
          >
            {isSubmitting ? "Swapping..." : "Swap"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
