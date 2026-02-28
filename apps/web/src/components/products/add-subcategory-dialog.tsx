"use client";

import { useState, useEffect } from "react";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useCategories } from "@/hooks/queries/use-categories";
import { useCreateChildCategoryMutation } from "@/hooks/mutations/use-category-mutations";
import type { Category } from "@/types/api";

interface AddSubcategoryDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialCategoryId?: string;
  onSubcategoryCreated?: (subcategory: Category) => void;
}

export function AddSubcategoryDialog({
  open,
  onOpenChange,
  initialCategoryId,
  onSubcategoryCreated,
}: AddSubcategoryDialogProps) {
  const { toast } = useToast();
  const [name, setName] = useState("");
  const [categoryId, setCategoryId] = useState(initialCategoryId ?? "");
  const { data: categories } = useCategories();
  const createMutation = useCreateChildCategoryMutation();

  useEffect(() => {
    if (open && initialCategoryId) {
      setCategoryId(initialCategoryId);
    }
  }, [open, initialCategoryId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim()) {
      toast({ title: "Error", description: "Subcategory name is required" });
      return;
    }

    if (!categoryId) {
      toast({ title: "Error", description: "Please select a category" });
      return;
    }

    try {
      const subcategory = await createMutation.mutateAsync({
        parentId: categoryId,
        name: name.trim(),
      });
      toast({ title: "Subcategory created" });
      onSubcategoryCreated?.(subcategory);
      setName("");
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to create subcategory";
      toast({ title: "Error", description: message });
    }
  };

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      setName("");
      setCategoryId(initialCategoryId ?? "");
    }
    onOpenChange(newOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Add Subcategory</DialogTitle>
          <DialogDescription>
            Create a new subcategory for a category.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label>Category</Label>
              <Select value={categoryId} onValueChange={setCategoryId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a category" />
                </SelectTrigger>
                <SelectContent>
                  {categories?.map((cat) => (
                    <SelectItem key={cat.id} value={cat.id}>
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="subcategory-name">Subcategory Name</Label>
              <Input
                id="subcategory-name"
                placeholder="e.g., Pokemon"
                value={name}
                onChange={(e) => setName(e.target.value)}
                autoFocus
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
