"use client";

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";

interface ProductDetailDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product: ProductWithInventory | null;
}

export function ProductDetailDialog({
  open,
  onOpenChange,
  product,
}: ProductDetailDialogProps) {
  if (!product) {
    return null;
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{product.product.name}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <p className="text-sm text-muted-foreground">
            Product details content will be implemented here.
          </p>
        </div>
      </DialogContent>
    </Dialog>
  );
}
