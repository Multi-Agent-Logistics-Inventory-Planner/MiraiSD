"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Minus, Pencil, Plus } from "lucide-react";
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
import { Checkbox } from "@/components/ui/checkbox";
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
import { useAuth } from "@/hooks/use-auth";
import { deleteProductImage, isUploadError } from "@/lib/supabase/storage";
import {
  useCategories,
  useChildCategories,
} from "@/hooks/queries/use-categories";
import {
  useCreateProductMutation,
  useUpdateProductMutation,
} from "@/hooks/mutations/use-product-mutations";
import { createInventory } from "@/lib/api/inventory";
import { LocationSelector } from "@/components/stock/location-selector";
import { AddCategoryDialog } from "./add-category-dialog";
import { AddSubcategoryDialog } from "./add-subcategory-dialog";
import { ManageCategoriesDialog } from "./manage-categories-dialog";
import type { Product, ProductRequest, Category } from "@/types/api";
import { LocationType } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

const NOT_ASSIGNED_LOCATION: LocationSelection = {
  locationType: LocationType.NOT_ASSIGNED,
  locationId: "__not_assigned__",
  locationCode: "",
};

const schema = z.object({
  sku: z.string().optional(),
  letter: z.string().max(50).optional(),
  name: z.string().min(1, "Name is required"),
  categoryId: z.string().min(1, "Category is required"),
  description: z.string().optional(),
  reorderPoint: z.coerce.number().int().min(0).optional(),
  targetStockLevel: z.coerce.number().int().min(0).optional(),
  leadTimeDays: z.coerce.number().int().min(0).optional(),
  unitCost: z.coerce.number().min(0).optional(),
  imageUrl: z.string().url("Must be a valid URL").optional().or(z.literal("")),
  notes: z.string().optional(),
  isActive: z.boolean().default(true),
});

type FormValues = z.infer<typeof schema>;

interface ProductFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialProduct?: Product | null;
  /** Parent product ID for creating child products (e.g., Kuji prizes) */
  parentId?: string | null;
  /** Parent product name for display */
  parentName?: string | null;
  /** Callback when a new product is created */
  onProductCreated?: (product: Product) => void;
}

export function ProductForm({
  open,
  onOpenChange,
  initialProduct,
  parentId,
  parentName,
  onProductCreated,
}: ProductFormProps) {
  const router = useRouter();
  const { toast } = useToast();
  const { user } = useAuth();
  const createMutation = useCreateProductMutation();
  const updateMutation = useUpdateProductMutation();
  const imageUpload = useImageUpload(initialProduct?.imageUrl);
  const { reset: resetImage } = imageUpload;

  const { data: categories, isLoading: categoriesLoading } = useCategories();
  const [addCategoryOpen, setAddCategoryOpen] = useState(false);
  const [addSubcategoryOpen, setAddSubcategoryOpen] = useState(false);
  const [manageCategoriesOpen, setManageCategoriesOpen] = useState(false);
  // Track root category and subcategory separately for UI
  const [rootCategoryId, setRootCategoryId] = useState("");
  const [subcategoryId, setSubcategoryId] = useState("");

  // Initial stock state (create mode only)
  const [initialStockEnabled, setInitialStockEnabled] = useState(false);
  const [initialStockLocation, setInitialStockLocation] =
    useState<LocationSelection>(NOT_ASSIGNED_LOCATION);
  const [locationError, setLocationError] = useState("");
  const [initialStockQty, setInitialStockQty] = useState<number | "">("");
  const [initialStockQtyError, setInitialStockQtyError] = useState("");
  const [isAddingStock, setIsAddingStock] = useState(false);

  const childCategories = useChildCategories(rootCategoryId);
  const hasChildCategories = childCategories.length > 0;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      sku: "",
      name: "",
      categoryId: "",
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
      // Determine root category and subcategory from the product's category
      const cat = initialProduct.category;
      if (cat.parentId) {
        // It's a subcategory, find its parent
        setRootCategoryId(cat.parentId);
        setSubcategoryId(cat.id);
        form.reset({
          sku: initialProduct.sku ?? "",
          letter: initialProduct.letter ?? "",
          name: initialProduct.name,
          categoryId: cat.id, // The actual category is the subcategory
          description: initialProduct.description ?? "",
          reorderPoint: initialProduct.reorderPoint ?? undefined,
          targetStockLevel: initialProduct.targetStockLevel ?? undefined,
          leadTimeDays: initialProduct.leadTimeDays ?? undefined,
          unitCost: initialProduct.unitCost ?? undefined,
          imageUrl: initialProduct.imageUrl ?? "",
          notes: initialProduct.notes ?? "",
          isActive: initialProduct.isActive ?? true,
        });
      } else {
        // It's a root category
        setRootCategoryId(cat.id);
        setSubcategoryId("");
        form.reset({
          sku: initialProduct.sku ?? "",
          name: initialProduct.name,
          categoryId: cat.id,
          description: initialProduct.description ?? "",
          reorderPoint: initialProduct.reorderPoint ?? undefined,
          targetStockLevel: initialProduct.targetStockLevel ?? undefined,
          leadTimeDays: initialProduct.leadTimeDays ?? undefined,
          unitCost: initialProduct.unitCost ?? undefined,
          imageUrl: initialProduct.imageUrl ?? "",
          notes: initialProduct.notes ?? "",
          isActive: initialProduct.isActive ?? true,
        });
      }
      resetImage(initialProduct.imageUrl);
    } else {
      setRootCategoryId("");
      setSubcategoryId("");
      form.reset();
      resetImage();
    }

    // Always reset initial stock fields when dialog opens/closes
    setInitialStockEnabled(false);
    setInitialStockLocation(NOT_ASSIGNED_LOCATION);
    setInitialStockQty("");
    setInitialStockQtyError("");
    setLocationError("");
  }, [open, initialProduct, form, resetImage]);

  // Update categoryId based on subcategory selection
  useEffect(() => {
    if (subcategoryId) {
      form.setValue("categoryId", subcategoryId, { shouldValidate: true });
    } else if (rootCategoryId) {
      form.setValue("categoryId", rootCategoryId, { shouldValidate: true });
    }
  }, [subcategoryId, rootCategoryId, form]);

  const isSaving =
    createMutation.isPending ||
    updateMutation.isPending ||
    imageUpload.isUploading ||
    isAddingStock;

  async function onSubmit(values: FormValues) {
    // Upload image first if a new file was selected
    let imageUrl: string | undefined = values.imageUrl || undefined;
    const oldImageUrl = initialProduct?.imageUrl;
    const isReplacingImage = imageUpload.hasNewFile && oldImageUrl;

    if (imageUpload.hasNewFile) {
      const uploadedUrl = await imageUpload.upload();
      if (uploadedUrl === null && imageUpload.error) {
        toast({ title: "Image upload failed", description: imageUpload.error });
        return;
      }
      imageUrl = uploadedUrl ?? undefined;
    }

    const payload: ProductRequest = {
      sku: values.sku?.trim() || undefined,
      letter: values.letter?.trim() ? values.letter.trim().slice(0, 50) : undefined,
      name: values.name.trim(),
      categoryId: values.categoryId, // This is either rootCategoryId or subcategoryId
      parentId: parentId || undefined, // Include parent ID if creating a child product
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

        // Delete old image from storage after successful update (non-blocking)
        if (isReplacingImage) {
          deleteProductImage(oldImageUrl).then((deleteResult) => {
            if (isUploadError(deleteResult)) {
              toast({
                title: "Note",
                description:
                  "Old image cleanup failed. Storage may need manual cleanup.",
                variant: "default",
              });
            }
          });
        }
      } else {
        const newProduct = await createMutation.mutateAsync(payload);
        toast({ title: "Product created" });
        onProductCreated?.(newProduct);

        // Optionally add initial stock after product creation
        if (initialStockEnabled && initialStockQty === "") {
          setInitialStockQtyError("Quantity is required");
          return;
        }

        if (
          initialStockEnabled &&
          (!initialStockLocation.locationType ||
            !initialStockLocation.locationId)
        ) {
          setLocationError("Location is required");
          return;
        }

        if (
          initialStockEnabled &&
          initialStockQty !== "" &&
          initialStockQty > 0 &&
          initialStockLocation.locationType &&
          initialStockLocation.locationId
        ) {
          setIsAddingStock(true);
          try {
            const actorId = user?.personId || user?.id;
            await createInventory(
              initialStockLocation.locationType,
              initialStockLocation.locationId,
              { itemId: newProduct.id, quantity: initialStockQty, actorId },
            );
            toast({ title: "Initial stock added" });
          } catch (stockErr: unknown) {
            const msg =
              stockErr instanceof Error
                ? stockErr.message
                : "Stock could not be added";
            toast({
              title: "Product created, but stock was not added",
              description: msg,
              variant: "destructive",
            });
          } finally {
            setIsAddingStock(false);
          }
        }

        // Check if this is a Kuji product - redirect to detail page to add prizes
        const selectedCategory = categories?.find((c) => c.id === rootCategoryId);
        const selectedSubcategory = subcategoryId
          ? childCategories.find((c) => c.id === subcategoryId)
          : null;
        const isKuji =
          selectedCategory?.name.toLowerCase() === "kuji" ||
          selectedCategory?.slug?.toLowerCase() === "kuji" ||
          selectedSubcategory?.name.toLowerCase() === "kuji" ||
          selectedSubcategory?.slug?.toLowerCase() === "kuji";

        if (isKuji && !parentId) {
          // Redirect to detail page so user can add prizes
          onOpenChange(false);
          router.push(`/products/${newProduct.id}`);
          return;
        }
      }
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Something went wrong";
      toast({ title: "Save failed", description: message });
    }
  }

  const handleCategoryCreated = (category: Category) => {
    setRootCategoryId(category.id);
    setSubcategoryId("");
    form.setValue("categoryId", category.id, { shouldValidate: true });
  };

  const handleSubcategoryCreated = (subcategory: Category) => {
    setSubcategoryId(subcategory.id);
    form.setValue("categoryId", subcategory.id, { shouldValidate: true });
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-lg max-h-[90vh] flex flex-col gap-0 p-0">
          <DialogHeader className="p-6">
            <DialogTitle>
              {initialProduct
                ? "Edit Product"
                : parentName
                  ? `Add Prize to ${parentName}`
                  : "Add Product"}
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
                <Label htmlFor="name">Product Name</Label>
                <Input
                  id="name"
                  placeholder="Enter product name"
                  {...form.register("name")}
                />
                {form.formState.errors.name?.message ? (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.name.message}
                  </p>
                ) : null}
              </div>

              {(initialProduct?.parentId || parentId) && (
                <div className="grid gap-2">
                  <Label htmlFor="letter">Letter or label</Label>
                  <Input
                    id="letter"
                    placeholder="A or Last Prize"
                    maxLength={50}
                    className="font-mono"
                    {...form.register("letter")}
                  />
                  {form.formState.errors.letter?.message ? (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.letter.message}
                    </p>
                  ) : null}
                </div>
              )}

              <div className="grid gap-2 pb-6">
                <Label htmlFor="description">
                  Description{" "}
                  <span className="text-muted-foreground font-normal">
                    (optional)
                  </span>
                </Label>
                <Input
                  id="description"
                  placeholder="Short description"
                  {...form.register("description")}
                />
              </div>

              <div className="grid gap-4">
                <div className="grid gap-2">
                  <div className="flex items-center gap-2">
                    <Label>Category</Label>
                    <button
                      type="button"
                      className="text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                      onClick={() => setManageCategoriesOpen(true)}
                      title="Manage categories"
                    >
                      <Pencil className="h-3.5 w-3.5" />
                    </button>
                  </div>
                  <div className="flex gap-2">
                    <Select
                      value={rootCategoryId}
                      onValueChange={(v) => {
                        setRootCategoryId(v);
                        setSubcategoryId("");
                      }}
                      disabled={categoriesLoading}
                    >
                      <SelectTrigger className="flex-1">
                        <SelectValue
                          placeholder={
                            categoriesLoading ? "Loading..." : "Select"
                          }
                        />
                      </SelectTrigger>
                      <SelectContent>
                        {categories?.map((c) => (
                          <SelectItem key={c.id} value={c.id}>
                            {c.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={() => setAddCategoryOpen(true)}
                      title="Add new category"
                    >
                      <Plus className="h-4 w-4" />
                    </Button>
                  </div>
                  {form.formState.errors.categoryId?.message ? (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.categoryId.message}
                    </p>
                  ) : null}
                </div>

                <div className="grid gap-2">
                  <Label>Subcategory</Label>
                  <div className="flex gap-2">
                    <Select
                      value={subcategoryId}
                      onValueChange={(v) =>
                        setSubcategoryId(v === "__none__" ? "" : v)
                      }
                      disabled={!hasChildCategories}
                    >
                      <SelectTrigger className="flex-1">
                        <SelectValue
                          placeholder={hasChildCategories ? "Select" : "N/A"}
                        />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="__none__">None</SelectItem>
                        {childCategories.map((s) => (
                          <SelectItem key={s.id} value={s.id}>
                            {s.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={() => setAddSubcategoryOpen(true)}
                      disabled={!rootCategoryId}
                      title="Add new subcategory"
                    >
                      <Plus className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="sku">
                    SKU{" "}
                    <span className="text-muted-foreground font-normal">
                      (optional)
                    </span>
                  </Label>
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
                  <Label htmlFor="unitCost">
                    Unit Cost{" "}
                    <span className="text-muted-foreground font-normal">
                      (optional)
                    </span>
                  </Label>
                  <Input
                    id="unitCost"
                    type="number"
                    step="0.01"
                    placeholder="0.00"
                    {...form.register("unitCost")}
                  />
                </div>
              </div>

              {/* Initial stock — create mode only */}
              {!initialProduct && (
                <div className="border rounded-lg p-4 space-y-4">
                  <label className="flex items-center gap-2 cursor-pointer select-none">
                    <Checkbox
                      checked={initialStockEnabled}
                      onCheckedChange={(checked) =>
                        setInitialStockEnabled(checked === true)
                      }
                      disabled={isSaving}
                    />
                    <span className="text-sm font-medium">
                      Add initial stock
                    </span>
                  </label>

                  {initialStockEnabled && (
                    <div className="space-y-4 pt-1">
                      <div className="grid gap-2">
                        <Label>Location</Label>
                        <LocationSelector
                          label=""
                          value={initialStockLocation}
                          onChange={(v) => {
                            setInitialStockLocation(v);
                            setLocationError("");
                          }}
                          disabled={isSaving}
                        />
                        {locationError && (
                          <p className="text-xs text-destructive">
                            {locationError}
                          </p>
                        )}
                      </div>

                      <div className="grid gap-2">
                        <Label htmlFor="initial-stock-qty">Quantity</Label>
                        <div className="flex items-center gap-2">
                          <Button
                            type="button"
                            variant="outline"
                            size="icon"
                            className="h-9 w-9 shrink-0"
                            disabled={
                              initialStockQty === "" ||
                              initialStockQty <= 1 ||
                              isSaving
                            }
                            onClick={() =>
                              setInitialStockQty((q) =>
                                q === "" ? 1 : Math.max(1, q - 1),
                              )
                            }
                          >
                            <Minus className="h-4 w-4" />
                          </Button>
                          <Input
                            id="initial-stock-qty"
                            type="number"
                            min={1}
                            className="text-center"
                            value={initialStockQty}
                            placeholder="0"
                            onChange={(e) => {
                              const raw = e.target.value;
                              if (raw === "") {
                                setInitialStockQty("");
                                setInitialStockQtyError("Quantity is required");
                              } else {
                                const v = parseInt(raw, 10);
                                if (!isNaN(v) && v >= 1) {
                                  setInitialStockQty(v);
                                  setInitialStockQtyError("");
                                }
                              }
                            }}
                            disabled={isSaving}
                          />

                          <Button
                            type="button"
                            variant="outline"
                            size="icon"
                            className="h-9 w-9 shrink-0"
                            disabled={isSaving}
                            onClick={() => {
                              setInitialStockQty((q) => (q === "" ? 1 : q + 1));
                              setInitialStockQtyError("");
                            }}
                          >
                            <Plus className="h-4 w-4" />
                          </Button>
                        </div>
                        {initialStockQtyError && (
                          <p className="text-xs text-destructive">
                            {initialStockQtyError}
                          </p>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            <DialogFooter className="px-6 py-4">
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={isSaving}
                className="text-white bg-[#0b66c2] hover:bg-[#0a5eb3] dark:bg-[#7c3aed] dark:hover:bg-[#6d28d9] dark:text-foreground"
              >
                {isSaving ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : null}
                {initialProduct ? "Save" : "Add Product"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AddCategoryDialog
        open={addCategoryOpen}
        onOpenChange={setAddCategoryOpen}
        onCategoryCreated={handleCategoryCreated}
      />

      <AddSubcategoryDialog
        open={addSubcategoryOpen}
        onOpenChange={setAddSubcategoryOpen}
        initialCategoryId={rootCategoryId}
        onSubcategoryCreated={handleSubcategoryCreated}
      />

      <ManageCategoriesDialog
        open={manageCategoriesOpen}
        onOpenChange={setManageCategoriesOpen}
      />
    </>
  );
}
