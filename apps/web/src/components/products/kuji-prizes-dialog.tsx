"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
import { PrizeForm } from "./prize-form";
import { useProductWithChildren } from "@/hooks/queries/use-products";
import { useDeleteProductMutation } from "@/hooks/mutations/use-product-mutations";
import { useToast } from "@/hooks/use-toast";
import { prizeLetterDisplay, sortPrizes } from "@/lib/utils";

interface KujiPrizesDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  productId: string;
  productName: string;
  categoryId: string;
}

export function KujiPrizesDialog({
  open,
  onOpenChange,
  productId,
  productName,
  categoryId,
}: KujiPrizesDialogProps) {
  const { toast } = useToast();
  const { data: product, refetch } = useProductWithChildren(open ? productId : null);
  const deleteMutation = useDeleteProductMutation();

  const [prizeFormOpen, setPrizeFormOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [prizeToDelete, setPrizeToDelete] = useState<{ id: string; letter: string } | null>(null);

  const prizes = product?.children ? sortPrizes(product.children) : [];

  function handleAddPrize() {
    setPrizeFormOpen(true);
  }

  function handlePrizeAdded() {
    refetch();
  }

  function handleDeleteClick(prize: { id: string; letter?: string | null }) {
    setPrizeToDelete({ id: prize.id, letter: prizeLetterDisplay(prize.letter) || "this prize" });
    setDeleteDialogOpen(true);
  }

  async function handleConfirmDelete() {
    if (!prizeToDelete) return;

    try {
      await deleteMutation.mutateAsync({ id: prizeToDelete.id, parentId: productId });
      toast({ title: "Prize deleted" });
      refetch();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to delete prize";
      toast({ title: "Error", description: message });
    } finally {
      setDeleteDialogOpen(false);
      setPrizeToDelete(null);
    }
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-lg max-h-[90vh] flex flex-col gap-0 p-0">
          <DialogHeader className="p-6 pb-4">
            <DialogTitle>Manage Prizes</DialogTitle>
            <DialogDescription>
              Add prizes to {productName}. Prizes will be sorted with LP first, then A, B, C, etc.
            </DialogDescription>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto px-6 pb-4">
            {prizes.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                <p>No prizes added yet.</p>
                <p className="text-sm mt-1">Click &quot;Add Prize&quot; to get started.</p>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-24">Letter</TableHead>
                    <TableHead className="text-right">Quantity</TableHead>
                    <TableHead className="w-12"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {prizes.map((prize) => (
                    <TableRow key={prize.id}>
                      <TableCell className="font-mono font-medium">
                        {prizeLetterDisplay(prize.letter) || "-"}
                      </TableCell>
                      <TableCell className="text-right">
                        {prize.quantity?.toLocaleString() ?? 0}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                          onClick={() => handleDeleteClick({ id: prize.id, letter: prize.letter })}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}

            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-4"
              onClick={handleAddPrize}
            >
              <Plus className="h-4 w-4 mr-1" />
              Add Prize
            </Button>
          </div>

          <DialogFooter className="px-6 py-4 border-t">
            <Button onClick={() => onOpenChange(false)}>
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <PrizeForm
        open={prizeFormOpen}
        onOpenChange={setPrizeFormOpen}
        parentId={productId}
        parentName={productName}
        parentCategoryId={categoryId}
        onSuccess={handlePrizeAdded}
      />

      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete prize?</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete Prize {prizeToDelete?.letter}? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={handleConfirmDelete}
              disabled={deleteMutation.isPending}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
