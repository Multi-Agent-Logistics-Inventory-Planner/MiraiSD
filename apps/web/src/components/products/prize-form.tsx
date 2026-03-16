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
import { useCreateProductMutation } from "@/hooks/mutations/use-product-mutations";

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
  onSuccess?: () => void;
}

export function PrizeForm({
  open,
  onOpenChange,
  parentId,
  parentName,
  parentCategoryId,
  onSuccess,
}: PrizeFormProps) {
  const { toast } = useToast();
  const createMutation = useCreateProductMutation();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      letter: "",
      templateQuantity: 0,
      quantity: 0,
    },
  });

  useEffect(() => {
    if (!open) {
      form.reset({ letter: "", templateQuantity: 0, quantity: 0 });
    }
  }, [open, form]);

  async function onSubmit(values: FormValues) {
    const quantity =
      values.quantity !== null && values.quantity !== undefined
        ? Number(values.quantity)
        : undefined;
    const hasInitialStock = quantity != null && quantity > 0;
    const letter = values.letter.trim().slice(0, 50);

    const templateQuantity =
      values.templateQuantity !== null && values.templateQuantity !== undefined
        ? Number(values.templateQuantity)
        : undefined;

    const payload = {
      parentId,
      categoryId: parentCategoryId,
      letter,
      templateQuantity,
      name: `Prize ${letter}`, // Auto-generate name from letter
      initialStock: hasInitialStock ? quantity : undefined,
    };

    try {
      await createMutation.mutateAsync(payload);
      toast({
        title: "Prize added",
        description: hasInitialStock
          ? `Initial stock of ${quantity} added.`
          : undefined,
      });

      onSuccess?.();
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Something went wrong";
      toast({ title: "Failed to add prize", description: message });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Add Prize to {parentName}</DialogTitle>
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
              disabled={createMutation.isPending}
              className="text-white bg-[#0b66c2] hover:bg-[#0a5eb3] dark:bg-[#7c3aed] dark:hover:bg-[#6d28d9] dark:text-foreground"
            >
              {createMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Add Prize
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
