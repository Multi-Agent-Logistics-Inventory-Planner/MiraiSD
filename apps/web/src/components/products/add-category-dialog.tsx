"use client";

import { useState } from "react";
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
import { useToast } from "@/hooks/use-toast";
import { useCreateCategoryMutation } from "@/hooks/mutations/use-category-mutations";
import type { Category } from "@/types/api";

interface AddCategoryDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCategoryCreated?: (category: Category) => void;
}

export function AddCategoryDialog({
  open,
  onOpenChange,
  onCategoryCreated,
}: AddCategoryDialogProps) {
  const { toast } = useToast();
  const [name, setName] = useState("");
  const createMutation = useCreateCategoryMutation();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim()) {
      toast({ title: "Error", description: "Category name is required" });
      return;
    }

    try {
      const category = await createMutation.mutateAsync({ name: name.trim() });
      toast({ title: "Category created" });
      onCategoryCreated?.(category);
      setName("");
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to create category";
      toast({ title: "Error", description: message });
    }
  };

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      setName("");
    }
    onOpenChange(newOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Add Category</DialogTitle>
          <DialogDescription>
            Create a new product category.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="category-name">Category Name</Label>
              <Input
                id="category-name"
                placeholder="e.g., Trading Cards"
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
