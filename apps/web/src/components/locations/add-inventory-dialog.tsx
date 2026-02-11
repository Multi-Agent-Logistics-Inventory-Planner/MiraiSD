"use client";

import { useEffect, useMemo, useState } from "react";
import { Check, ChevronsUpDown, Loader2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { getProducts } from "@/lib/api/products";
import type { Product, LocationType, InventoryRequest, Inventory } from "@/types/api";

interface AddInventoryDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  locationType: LocationType;
  locationId: string;
  existingInventory?: Inventory[];
  isSaving?: boolean;
  onSubmit: (payload: InventoryRequest, isUpdate: boolean, inventoryId?: string) => Promise<void> | void;
}

export function AddInventoryDialog({
  open,
  onOpenChange,
  existingInventory = [],
  isSaving,
  onSubmit,
}: AddInventoryDialogProps) {
  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: getProducts,
  });

  const products = productsQuery.data ?? [];

  const [productId, setProductId] = useState<string>("");
  const [qty, setQty] = useState<string>("1");
  const [comboOpen, setComboOpen] = useState(false);

  const selected: Product | undefined = useMemo(
    () => products.find((p) => p.id === productId),
    [products, productId]
  );

  // Check if the selected product already exists in the location
  const existingItem = useMemo(
    () => existingInventory.find((inv) => inv.item.id === productId),
    [existingInventory, productId]
  );

  // When selecting a product that exists, pre-fill with current quantity
  useEffect(() => {
    if (existingItem) {
      setQty(String(existingItem.quantity));
    } else {
      setQty("1");
    }
  }, [existingItem]);

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setProductId("");
      setQty("1");
    }
  }, [open]);

  async function handleSubmit() {
    const quantity = Number(qty);
    if (!productId || !Number.isFinite(quantity) || quantity <= 0) return;

    const isUpdate = Boolean(existingItem);
    await onSubmit({ itemId: productId, quantity }, isUpdate, existingItem?.id);
    setProductId("");
    setQty("1");
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{existingItem ? "Update Inventory" : "Add Inventory"}</DialogTitle>
          <DialogDescription>
            {existingItem
              ? "Update the quantity for this product in this location."
              : "Add a product and quantity to this location."
            }
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid gap-2">
            <Label>Product</Label>
            <Popover open={comboOpen} onOpenChange={setComboOpen}>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  className="justify-between"
                  disabled={productsQuery.isLoading}
                >
                  {selected ? `${selected.name} (${selected.sku})` : "Select product..."}
                  <ChevronsUpDown className="ml-2 h-4 w-4 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-[360px] p-0" align="start">
                <Command>
                  <CommandInput placeholder="Search products..." />
                  <CommandList>
                    <CommandEmpty>No products found.</CommandEmpty>
                    <CommandGroup>
                      {products.map((p) => {
                        const existingInv = existingInventory.find((inv) => inv.item.id === p.id);
                        return (
                          <CommandItem
                            key={p.id}
                            value={`${p.name} ${p.sku}`}
                            onSelect={() => {
                              setProductId(p.id);
                              setComboOpen(false);
                            }}
                          >
                            <Check
                              className={cn(
                                "mr-2 h-4 w-4",
                                productId === p.id ? "opacity-100" : "opacity-0"
                              )}
                            />
                            <div className="flex flex-col flex-1">
                              <div className="flex items-center gap-2">
                                <span className="text-sm">{p.name}</span>
                                {existingInv && (
                                  <Badge variant="secondary" className="text-xs">
                                    {existingInv.quantity} in stock
                                  </Badge>
                                )}
                              </div>
                              <span className="text-xs text-muted-foreground">{p.sku}</span>
                            </div>
                          </CommandItem>
                        );
                      })}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
            {productsQuery.isError ? (
              <p className="text-xs text-destructive">
                Failed to load products.
              </p>
            ) : null}
          </div>

          {existingItem && (
            <div className="rounded-md bg-muted p-3 text-sm">
              <p className="text-muted-foreground">
                This product already exists in this location with{" "}
                <span className="font-medium text-foreground">{existingItem.quantity}</span> units.
                Enter the new total quantity below.
              </p>
            </div>
          )}

          <div className="grid gap-2">
            <Label htmlFor="qty">
              {existingItem ? "New Quantity" : "Quantity"}
            </Label>
            <Input
              id="qty"
              type="number"
              min={1}
              value={qty}
              onChange={(e) => setQty(e.target.value)}
            />
          </div>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={Boolean(isSaving) || !productId}
          >
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            {existingItem ? "Update" : "Add"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
