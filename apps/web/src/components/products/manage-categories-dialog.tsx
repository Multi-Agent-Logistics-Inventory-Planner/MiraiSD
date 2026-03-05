"use client";

import { useState } from "react";
import { Trash2, ChevronRight, Loader2 } from "lucide-react";
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
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";
import { useCategories } from "@/hooks/queries/use-categories";
import { useDeleteCategoryMutation } from "@/hooks/mutations/use-category-mutations";
import type { Category } from "@/types/api";

interface ManageCategoriesDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

interface PendingDelete {
  id: string;
  name: string;
  isSubcategory: boolean;
}

export function ManageCategoriesDialog({
  open,
  onOpenChange,
}: ManageCategoriesDialogProps) {
  const { toast } = useToast();
  const { data: categories, isLoading } = useCategories();
  const deleteMutation = useDeleteCategoryMutation();

  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(
    null
  );
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const toggleExpanded = (id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleDeleteClick = (category: Category, isSubcategory: boolean) => {
    setPendingDelete({ id: category.id, name: category.name, isSubcategory });
  };

  const handleConfirmDelete = async () => {
    if (!pendingDelete) return;

    try {
      await deleteMutation.mutateAsync({ id: pendingDelete.id });
      toast({
        title: `${pendingDelete.isSubcategory ? "Subcategory" : "Category"} deleted`,
        description: `"${pendingDelete.name}" has been removed.`,
      });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to delete";
      toast({
        title: "Cannot delete",
        description: message,
        variant: "destructive",
      });
    } finally {
      setPendingDelete(null);
    }
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-md max-h-[80vh] flex flex-col gap-0 p-0">
          <DialogHeader className="px-6 pt-6 pb-4">
            <DialogTitle>Manage Categories</DialogTitle>
            <DialogDescription>
              Delete categories and subcategories. Categories in use by products
              cannot be deleted.
            </DialogDescription>
          </DialogHeader>

          <div className="overflow-y-auto px-6 pb-6 flex-1">
            {isLoading ? (
              <div className="flex items-center justify-center py-8 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin mr-2" />
                Loading categories...
              </div>
            ) : !categories || categories.length === 0 ? (
              <p className="text-sm text-muted-foreground py-4 text-center">
                No categories found
              </p>
            ) : (
              <ul className="space-y-1">
                {categories.map((category) => {
                  const hasChildren = category.children.length > 0;
                  const isExpanded = expandedIds.has(category.id);

                  return (
                    <li key={category.id}>
                      <div className="flex items-center gap-2 py-2 px-2 rounded-md hover:bg-muted/50 group">
                        <button
                          type="button"
                          className="flex items-center gap-2 flex-1 text-left"
                          onClick={() =>
                            hasChildren ? toggleExpanded(category.id) : undefined
                          }
                          disabled={!hasChildren}
                        >
                          <ChevronRight
                            className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform ${
                              isExpanded ? "rotate-90" : ""
                            } ${!hasChildren ? "invisible" : ""}`}
                          />
                          <span className="text-sm font-medium">
                            {category.name}
                          </span>
                          {hasChildren && (
                            <Badge variant="secondary" className="text-xs">
                              {category.children.length}
                            </Badge>
                          )}
                        </button>

                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="h-7 w-7 shrink-0 opacity-0 group-hover:opacity-100 text-destructive hover:text-destructive hover:bg-destructive/10"
                          onClick={() => handleDeleteClick(category, false)}
                          title={`Delete ${category.name}`}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>

                      {hasChildren && isExpanded && (
                        <ul className="ml-6 mt-1 space-y-1 border-l pl-4">
                          {category.children.map((child) => (
                            <li key={child.id}>
                              <div className="flex items-center gap-2 py-1.5 px-2 rounded-md hover:bg-muted/50 group">
                                <span className="flex-1 text-sm text-muted-foreground">
                                  {child.name}
                                </span>
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="icon"
                                  className="h-7 w-7 shrink-0 opacity-0 group-hover:opacity-100 text-destructive hover:text-destructive hover:bg-destructive/10"
                                  onClick={() =>
                                    handleDeleteClick(child, true)
                                  }
                                  title={`Delete ${child.name}`}
                                >
                                  <Trash2 className="h-3.5 w-3.5" />
                                </Button>
                              </div>
                            </li>
                          ))}
                        </ul>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={!!pendingDelete}
        onOpenChange={(o) => !o && setPendingDelete(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              Delete {pendingDelete?.isSubcategory ? "subcategory" : "category"}?
            </AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete{" "}
              <span className="font-medium text-foreground">
                &ldquo;{pendingDelete?.name}&rdquo;
              </span>
              ? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
