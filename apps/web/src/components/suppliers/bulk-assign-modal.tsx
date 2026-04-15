"use client";

import { useState, useMemo } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { Search } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { useProducts } from "@/hooks/queries/use-products";
import { useBulkAssignProductsMutation } from "@/hooks/mutations/use-supplier-mutations";
import type { Supplier } from "@/types/api";

interface BulkAssignModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  supplier: Supplier | null;
}

export function BulkAssignModal({ open, onOpenChange, supplier }: BulkAssignModalProps) {
  const { toast } = useToast();
  const [search, setSearch] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const { data: products, isLoading } = useProducts({ rootOnly: true });
  const bulkAssignMutation = useBulkAssignProductsMutation();

  // Filter products by search
  const filteredProducts = useMemo(() => {
    if (!products) return [];
    if (!search.trim()) return products;
    const query = search.toLowerCase();
    return products.filter(
      (p) =>
        p.name.toLowerCase().includes(query) ||
        p.sku?.toLowerCase().includes(query)
    );
  }, [products, search]);

  const handleToggle = (productId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(productId)) {
        next.delete(productId);
      } else {
        next.add(productId);
      }
      return next;
    });
  };

  const handleSelectAll = () => {
    if (selectedIds.size === filteredProducts.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredProducts.map((p) => p.id)));
    }
  };

  const handleSubmit = async () => {
    if (!supplier || selectedIds.size === 0) return;

    try {
      const count = await bulkAssignMutation.mutateAsync({
        supplierId: supplier.id,
        productIds: Array.from(selectedIds),
      });
      toast({ title: `Assigned ${count} products to ${supplier.displayName}`, variant: "success" });
      setSelectedIds(new Set());
      setSearch("");
      onOpenChange(false);
    } catch {
      toast({ title: "Error", description: "Failed to assign products" });
    }
  };

  const handleClose = () => {
    setSelectedIds(new Set());
    setSearch("");
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Assign Products to {supplier?.displayName}</DialogTitle>
          <DialogDescription>
            Select products to set this supplier as their preferred supplier.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Search */}
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search products..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>

          {/* Select All */}
          <div className="flex items-center gap-2 px-2">
            <Checkbox
              id="select-all"
              checked={
                filteredProducts.length > 0 &&
                selectedIds.size === filteredProducts.length
              }
              onCheckedChange={handleSelectAll}
            />
            <label htmlFor="select-all" className="text-sm cursor-pointer">
              Select All ({filteredProducts.length})
            </label>
            {selectedIds.size > 0 && (
              <span className="text-sm text-muted-foreground ml-auto">
                {selectedIds.size} selected
              </span>
            )}
          </div>

          {/* Product List */}
          <ScrollArea className="h-64 border rounded-md">
            <div className="p-2 space-y-1">
              {isLoading ? (
                Array.from({ length: 8 }).map((_, i) => (
                  <div key={i} className="flex items-center gap-2 p-2">
                    <Skeleton className="h-4 w-4" />
                    <Skeleton className="h-4 flex-1" />
                  </div>
                ))
              ) : filteredProducts.length === 0 ? (
                <div className="text-center text-muted-foreground py-8">
                  No products found
                </div>
              ) : (
                filteredProducts.map((product) => (
                  <div
                    key={product.id}
                    className="flex items-center gap-2 p-2 hover:bg-muted rounded cursor-pointer"
                    onClick={() => handleToggle(product.id)}
                  >
                    <Checkbox
                      checked={selectedIds.has(product.id)}
                      onCheckedChange={() => handleToggle(product.id)}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="font-medium truncate">{product.name}</div>
                      {product.sku && (
                        <div className="text-xs text-muted-foreground">
                          {product.sku}
                        </div>
                      )}
                    </div>
                    {product.preferredSupplierName && (
                      <span className="text-xs text-muted-foreground">
                        Current: {product.preferredSupplierName}
                      </span>
                    )}
                  </div>
                ))
              )}
            </div>
          </ScrollArea>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={selectedIds.size === 0 || bulkAssignMutation.isPending}
          >
            {bulkAssignMutation.isPending
              ? "Assigning..."
              : `Assign ${selectedIds.size} Product${selectedIds.size !== 1 ? "s" : ""}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
