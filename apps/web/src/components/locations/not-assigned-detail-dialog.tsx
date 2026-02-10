"use client";

import { useState } from "react";
import { Pencil, Trash2, Check, X, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Can, Permission } from "@/components/rbac";
import type { NotAssignedInventory } from "@/types/api";
import {
  useUpdateNotAssignedInventoryMutation,
  useDeleteNotAssignedInventoryMutation,
} from "@/hooks/mutations/use-not-assigned-mutations";
import { useToast } from "@/hooks/use-toast";

interface NotAssignedDetailDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  inventory: NotAssignedInventory | null;
}

export function NotAssignedDetailDialog({
  open,
  onOpenChange,
  inventory,
}: NotAssignedDetailDialogProps) {
  const { toast } = useToast();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [quantityInput, setQuantityInput] = useState("");

  const updateMutation = useUpdateNotAssignedInventoryMutation();
  const deleteMutation = useDeleteNotAssignedInventoryMutation();

  if (!inventory) {
    return null;
  }

  function handleStartEdit() {
    setQuantityInput(String(inventory?.quantity ?? 0));
    setIsEditing(true);
  }

  function handleCancelEdit() {
    setIsEditing(false);
    setQuantityInput("");
  }

  async function handleSaveQuantity() {
    const quantity = parseInt(quantityInput, 10);
    if (isNaN(quantity) || quantity < 0) {
      toast({ title: "Invalid quantity", description: "Please enter a valid number" });
      return;
    }

    try {
      await updateMutation.mutateAsync({
        inventoryId: inventory!.id,
        payload: { itemId: inventory!.item.id, quantity },
      });
      toast({ title: "Quantity updated" });
      setIsEditing(false);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Update failed";
      toast({ title: "Update failed", description: msg });
    }
  }

  async function handleDelete() {
    try {
      await deleteMutation.mutateAsync({ inventoryId: inventory!.id });
      toast({ title: "Item removed from unassigned inventory" });
      onOpenChange(false);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Delete failed";
      toast({ title: "Delete failed", description: msg });
    }
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto p-0">
          <DialogHeader className="px-6 pt-6 pb-4">
            <DialogTitle className="flex items-center gap-3">
              <span className="text-xl font-semibold">{inventory.item.name}</span>
              <Badge variant="outline" className="text-xs">
                {inventory.item.category}
              </Badge>
            </DialogTitle>
          </DialogHeader>

          <div className="px-6 pb-6 space-y-6">
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <Label className="text-muted-foreground text-xs">SKU</Label>
                <p className="font-mono font-medium">{inventory.item.sku}</p>
              </div>
              <div>
                <Label className="text-muted-foreground text-xs">Category</Label>
                <p className="font-medium">{inventory.item.category}</p>
              </div>
            </div>

            <div className="border rounded-lg p-4">
              <div className="flex items-center justify-between">
                <div>
                  <Label className="text-muted-foreground text-xs">Quantity</Label>
                  {isEditing ? (
                    <div className="flex items-center gap-2 mt-1">
                      <Input
                        type="number"
                        min="0"
                        value={quantityInput}
                        onChange={(e) => setQuantityInput(e.target.value)}
                        className="w-24 h-8"
                        autoFocus
                        disabled={updateMutation.isPending}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") handleSaveQuantity();
                          if (e.key === "Escape") handleCancelEdit();
                        }}
                      />
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={handleSaveQuantity}
                        disabled={updateMutation.isPending}
                      >
                        {updateMutation.isPending ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Check className="h-4 w-4" />
                        )}
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={handleCancelEdit}
                        disabled={updateMutation.isPending}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  ) : (
                    <p className="text-2xl font-semibold">{inventory.quantity}</p>
                  )}
                </div>
                {!isEditing && (
                  <div className="flex items-center gap-2">
                    <Can permission={Permission.STORAGE_UPDATE}>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={handleStartEdit}
                      >
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
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove from unassigned inventory?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently remove{" "}
              <span className="font-medium">{inventory.item.name}</span> from
              the unassigned inventory.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={handleDelete}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
