"use client";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Trash2 } from "lucide-react";

interface DeleteProductDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  productName: string;
  totalQuantity: number;
  isPending: boolean;
  onDelete: () => void;
}

export function DeleteProductDialog({
  open,
  onOpenChange,
  productName,
  totalQuantity,
  isPending,
  onDelete,
}: DeleteProductDialogProps) {
  const hasInventory = totalQuantity > 0;

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogTrigger asChild>
        <Button size="sm" variant="destructive">
          <Trash2 className="h-4 w-4 sm:mr-1" />
          <span className="hidden sm:inline">Delete</span>
          <span className="sr-only sm:hidden">Delete</span>
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {hasInventory ? "Cannot Delete Product" : "Delete Product"}
          </AlertDialogTitle>
          <AlertDialogDescription>
            {hasInventory ? (
              <>
                This product cannot be deleted because it still has{" "}
                <span className="font-semibold">{totalQuantity}</span> units in
                storage locations. Please remove all inventory from storage
                locations before deleting this product.
              </>
            ) : (
              <>
                Are you sure you want to delete{" "}
                <span className="font-semibold">{productName}</span>? This
                action cannot be undone.
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          {!hasInventory && (
            <AlertDialogAction
              onClick={onDelete}
              disabled={isPending}
              className="bg-red-600 text-white hover:bg-red-700"
            >
              {isPending ? "Deleting..." : "Delete"}
            </AlertDialogAction>
          )}
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
