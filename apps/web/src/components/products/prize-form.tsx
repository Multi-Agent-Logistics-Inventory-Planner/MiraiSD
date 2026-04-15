"use client";

import { useEffect } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import {
  useCreateProductMutation,
  useUpdateProductMutation,
} from "@/hooks/mutations/use-product-mutations";
import type { ProductSummary } from "@/types/api";

const schema = z.object({
  letter: z.string().min(1, "Letter is required").max(50, "Max 50 characters"),
  templateQuantity: z
    .union([z.string(), z.number()])
    .transform((v) => (v === "" || v === undefined ? 0 : Number(v)))
    .refine((v) => Number.isInteger(v) && v >= 0, "Must be 0 or greater"),
  quantity: z
    .union([z.string(), z.number()])
    .transform((v) => (v === "" || v === undefined ? 0 : Number(v)))
    .refine((v) => Number.isInteger(v) && v >= 0, "Must be 0 or greater"),
});

type FormValues = z.infer<typeof schema>;

interface PrizeFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  parentId: string;
  parentName: string;
  parentCategoryId: string;
  /** Prize to edit - if provided, form is in edit mode */
  prize?: ProductSummary | null;
  onSuccess?: () => void;
}

export function PrizeForm({
  open,
  onOpenChange,
  parentId,
  parentName,
  parentCategoryId,
  prize,
  onSuccess,
}: PrizeFormProps) {
  const { toast } = useToast();
  const createMutation = useCreateProductMutation();
  const updateMutation = useUpdateProductMutation();

  const isEditing = !!prize;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      letter: "",
      templateQuantity: 0,
      quantity: 0,
    },
  });

  useEffect(() => {
    if (open && prize) {
      // Edit mode - populate form with prize data
      form.reset({
        letter: prize.letter ?? "",
        templateQuantity: prize.templateQuantity ?? 0,
        quantity: prize.quantity ?? 0,
      });
    } else if (!open) {
      // Reset form when dialog closes
      form.reset({ letter: "", templateQuantity: 0, quantity: 0 });
    }
  }, [open, prize, form]);

  async function onSubmit(values: FormValues) {
    const quantity = Number(values.quantity);
    const letter = values.letter.trim().slice(0, 50);
    const templateQuantity = Number(values.templateQuantity);

    try {
      if (isEditing && prize) {
        // Update existing prize
        await updateMutation.mutateAsync({
          id: prize.id,
          payload: {
            letter,
            templateQuantity,
            quantity,
            name: `Prize ${letter}`,
          },
        });
        toast({ title: "Prize updated", variant: "success" });
      } else {
        // Create new prize
        const hasInitialStock = quantity > 0;
        const payload = {
          parentId,
          categoryId: parentCategoryId,
          letter,
          templateQuantity,
          name: `Prize ${letter}`,
          initialStock: hasInitialStock ? quantity : undefined,
        };
        await createMutation.mutateAsync(payload);
        toast({
          title: "Prize added",
          description: hasInitialStock
            ? `Initial stock of ${quantity} added.`
            : undefined,
          variant: "success",
        });
      }

      onSuccess?.();
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Something went wrong";
      toast({ title: isEditing ? "Failed to update prize" : "Failed to add prize", description: message });
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? `Edit Prize ${prize?.letter ?? ""}` : `Add Prize to ${parentName}`}
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="letter">Letter</Label>
              <Input
                id="letter"
                placeholder="A, B, LP..."
                maxLength={50}
                className="font-mono"
                {...form.register("letter")}
              />
              {form.formState.errors.letter?.message && (
                <p className="text-xs text-destructive">
                  {form.formState.errors.letter.message}
                </p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="templateQuantity">Qty/Set</Label>
              <Input
                id="templateQuantity"
                type="number"
                min={0}
                placeholder="e.g. 10"
                {...form.register("templateQuantity")}
              />
              {form.formState.errors.templateQuantity?.message && (
                <p className="text-xs text-destructive">
                  {form.formState.errors.templateQuantity.message}
                </p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="quantity">Pieces</Label>
              <Input
                id="quantity"
                type="number"
                min={0}
                placeholder="0"
                {...form.register("quantity")}
              />
              {form.formState.errors.quantity?.message && (
                <p className="text-xs text-destructive">
                  {form.formState.errors.quantity.message}
                </p>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isPending}
              className="text-white bg-[#0b66c2] hover:bg-[#0a5eb3] dark:bg-[#7c3aed] dark:hover:bg-[#6d28d9] dark:text-foreground"
            >
              {isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              {isEditing ? "Save" : "Add Prize"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
