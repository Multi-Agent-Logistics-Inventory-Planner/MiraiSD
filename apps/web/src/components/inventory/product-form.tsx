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
import { ImageUpload } from "@/components/ui/image-upload";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useImageUpload } from "@/hooks/use-image-upload";
import {
  ProductCategory,
  ProductSubcategory,
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
  type Product,
  type ProductRequest,
} from "@/types/api";
import {
  useCreateProductMutation,
  useUpdateProductMutation,
} from "@/hooks/mutations/use-product-mutations";

const schema = z
  .object({
    sku: z.string().min(1, "SKU is required"),
    name: z.string().min(1, "Name is required"),
    category: z.string().min(1, "Category is required"),
    subcategory: z.string().optional(),
    description: z.string().optional(),
    reorderPoint: z.coerce.number().int().min(0).optional(),
    targetStockLevel: z.coerce.number().int().min(0).optional(),
    leadTimeDays: z.coerce.number().int().min(0).optional(),
    unitCost: z.coerce.number().min(0).optional(),
    imageUrl: z
      .string()
      .url("Must be a valid URL")
      .optional()
      .or(z.literal("")),
    notes: z.string().optional(),
    isActive: z.boolean().default(true),
  })
  .refine(
    (data) => !data.subcategory || data.category === ProductCategory.BLIND_BOX,
    {
      message: "Subcategory is only allowed for Blind Box products",
      path: ["subcategory"],
    },
  );

type FormValues = z.infer<typeof schema>;

interface ProductFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialProduct?: Product | null;
}

export function ProductForm({
  open,
  onOpenChange,
  initialProduct,
}: ProductFormProps) {
  const { toast } = useToast();
  const createMutation = useCreateProductMutation();
  const updateMutation = useUpdateProductMutation();
  const imageUpload = useImageUpload(initialProduct?.imageUrl);
  const { reset: resetImage } = imageUpload;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      sku: "",
      name: "",
      category: "",
      subcategory: "",
      description: "",
      reorderPoint: undefined,
      targetStockLevel: undefined,
      leadTimeDays: undefined,
      unitCost: undefined,
      imageUrl: "",
      notes: "",
      isActive: true,
    },
  });

  useEffect(() => {
    if (!open) return;

    if (initialProduct) {
      form.reset({
        sku: initialProduct.sku,
        name: initialProduct.name,
        category: initialProduct.category,
        subcategory: initialProduct.subcategory ?? "",
        description: initialProduct.description ?? "",
        reorderPoint: initialProduct.reorderPoint ?? undefined,
        targetStockLevel: initialProduct.targetStockLevel ?? undefined,
        leadTimeDays: initialProduct.leadTimeDays ?? undefined,
        unitCost: initialProduct.unitCost ?? undefined,
        imageUrl: initialProduct.imageUrl ?? "",
        notes: initialProduct.notes ?? "",
        isActive: initialProduct.isActive ?? true,
      });
      resetImage(initialProduct.imageUrl);
    } else {
      form.reset();
      resetImage();
    }
  }, [open, initialProduct, form, resetImage]);

  const isSaving =
    createMutation.isPending ||
    updateMutation.isPending ||
    imageUpload.isUploading;

  async function onSubmit(values: FormValues) {
    // Upload image first if a new file was selected
    let imageUrl: string | undefined = values.imageUrl || undefined;
    if (imageUpload.hasNewFile) {
      const uploadedUrl = await imageUpload.upload();
      if (uploadedUrl === null && imageUpload.error) {
        toast({ title: "Image upload failed", description: imageUpload.error });
        return;
      }
      imageUrl = uploadedUrl ?? undefined;
    }

    const payload: ProductRequest = {
      sku: values.sku.trim(),
      name: values.name.trim(),
      category: values.category as ProductCategory,
      subcategory: values.subcategory
        ? (values.subcategory as ProductSubcategory)
        : undefined,
      description: values.description || undefined,
      reorderPoint: values.reorderPoint,
      targetStockLevel: values.targetStockLevel,
      leadTimeDays: values.leadTimeDays,
      unitCost: values.unitCost,
      imageUrl,
      notes: values.notes || undefined,
      isActive: values.isActive,
    };

    try {
      if (initialProduct) {
        await updateMutation.mutateAsync({ id: initialProduct.id, payload });
        toast({ title: "Product updated" });
      } else {
        await createMutation.mutateAsync(payload);
        toast({ title: "Product created" });
      }
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Something went wrong";
      toast({ title: "Save failed", description: message });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] flex flex-col gap-0 p-0">
        <DialogHeader className="p-6">
          <DialogTitle>
            {initialProduct ? "Edit Item" : "Add Item"}
          </DialogTitle>
        </DialogHeader>

        <form
          onSubmit={form.handleSubmit(onSubmit)}
          className="flex flex-col flex-1 min-h-0"
        >
          <div className="overflow-y-auto px-6 pb-4 space-y-4">
            <div className="grid gap-2">
              <Label>Product Image</Label>
              <ImageUpload
                displayUrl={imageUpload.displayUrl}
                isUploading={imageUpload.isUploading}
                error={imageUpload.error}
                hasNewFile={imageUpload.hasNewFile}
                onFileSelect={imageUpload.selectFile}
                onClear={imageUpload.clear}
                disabled={isSaving}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="name">Item Name</Label>
              <Input
                id="name"
                placeholder="Enter item name"
                {...form.register("name")}
              />
              {form.formState.errors.name?.message ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.name.message}
                </p>
              ) : null}
            </div>

            <div className="grid gap-2 pb-6">
              <Label htmlFor="description">Description (optional)</Label>
              <Input
                id="description"
                placeholder="Short description"
                {...form.register("description")}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label>Category</Label>
                <Select
                  value={form.watch("category")}
                  onValueChange={(v) => {
                    form.setValue("category", v, { shouldValidate: true });
                    if (v !== ProductCategory.BLIND_BOX) {
                      form.setValue("subcategory", "");
                    }
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select" />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.values(ProductCategory).map((c) => (
                      <SelectItem key={c} value={c}>
                        {PRODUCT_CATEGORY_LABELS[c]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {form.formState.errors.category?.message ? (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.category.message}
                  </p>
                ) : null}
              </div>

              <div className="grid gap-2">
                <Label>Subcategory</Label>
                <Select
                  value={form.watch("subcategory") || ""}
                  onValueChange={(v) =>
                    form.setValue("subcategory", v === "__none__" ? "" : v)
                  }
                  disabled={
                    form.watch("category") !== ProductCategory.BLIND_BOX
                  }
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">None</SelectItem>
                    {Object.values(ProductSubcategory).map((s) => (
                      <SelectItem key={s} value={s}>
                        {PRODUCT_SUBCATEGORY_LABELS[s]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {form.formState.errors.subcategory?.message ? (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.subcategory.message}
                  </p>
                ) : null}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="sku">SKU</Label>
                <Input
                  id="sku"
                  placeholder="SKU-XXX"
                  {...form.register("sku")}
                />
                {form.formState.errors.sku?.message ? (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.sku.message}
                  </p>
                ) : null}
              </div>

              <div className="grid gap-2">
                <Label htmlFor="unitCost">Unit Cost ($)</Label>
                <Input
                  id="unitCost"
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  {...form.register("unitCost")}
                />
              </div>
            </div>
          </div>

          <DialogFooter className="px-6 py-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              {initialProduct ? "Save" : "Add Product"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
