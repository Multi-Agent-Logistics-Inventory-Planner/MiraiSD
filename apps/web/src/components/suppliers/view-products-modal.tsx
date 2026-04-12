"use client";

import { useState, useMemo } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { Search } from "lucide-react";
import { useSupplierProducts } from "@/hooks/queries/use-suppliers";
import type { Supplier } from "@/types/api";

interface ViewProductsModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  supplier: Supplier | null;
}

export function ViewProductsModal({ open, onOpenChange, supplier }: ViewProductsModalProps) {
  const [search, setSearch] = useState("");

  const { data: products, isLoading } = useSupplierProducts(
    open && supplier ? supplier.id : null
  );

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

  const handleClose = () => {
    setSearch("");
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Products for {supplier?.displayName}</DialogTitle>
          <DialogDescription>
            {products?.length === 0
              ? "No products are currently assigned to this supplier."
              : `${products?.length ?? 0} product${products?.length !== 1 ? "s" : ""} assigned to this supplier.`}
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

          {/* Product List */}
          <ScrollArea className="h-64 border rounded-md">
            <div className="p-2 space-y-1">
              {isLoading ? (
                Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="flex items-center gap-2 p-2">
                    <Skeleton className="h-4 flex-1" />
                    <Skeleton className="h-5 w-14" />
                  </div>
                ))
              ) : filteredProducts.length === 0 ? (
                <div className="text-center text-muted-foreground py-8">
                  {search ? "No products match your search" : "No products assigned"}
                </div>
              ) : (
                filteredProducts.map((product) => (
                  <div
                    key={product.id}
                    className="flex items-center gap-2 p-2 hover:bg-muted rounded"
                  >
                    <div className="flex-1 min-w-0">
                      <div className="font-medium truncate">{product.name}</div>
                      {product.sku && (
                        <div className="text-xs text-muted-foreground">
                          {product.sku}
                        </div>
                      )}
                    </div>
                    <Badge variant={product.preferredSupplierAuto ? "secondary" : "outline"}>
                      {product.preferredSupplierAuto ? "Auto" : "Manual"}
                    </Badge>
                  </div>
                ))
              )}
            </div>
          </ScrollArea>
        </div>
      </DialogContent>
    </Dialog>
  );
}
