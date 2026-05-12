"use client";

import { useEffect, useMemo, useState } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Pencil, Plus } from "lucide-react";
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
import { QuantityInput } from "@/components/ui/quantity-input";
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
import { usePermissions } from "@/hooks/use-permissions";
import { Permission } from "@/lib/rbac/permissions";
import { deleteProductImage, isUploadError } from "@/lib/supabase/storage";
import {
  useCategories,
  useChildCategories,
} from "@/hooks/queries/use-categories";
import {
  useCreateProductMutation,
  useDeleteProductMutation,
  useUpdateProductMutation,
} from "@/hooks/mutations/use-product-mutations";
import { createInventory } from "@/lib/api/inventory";
import { LocationSelector } from "@/components/stock/location-selector";
import { AddCategoryDialog } from "./add-category-dialog";
import { AddSubcategoryDialog } from "./add-subcategory-dialog";
import { ManageCategoriesDialog } from "./manage-categories-dialog";
import { DeleteProductDialog } from "./delete-product-dialog";
import { PrizeTableInline, type PendingPrize } from "./prize-table-inline";
import { SupplierAutocomplete } from "@/components/suppliers";
import type { Product, ProductRequest, Category } from "@/types/api";
import { KujiType, LocationType } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";
import { buildKujiCategoryIds, buildPackCategoryIds } from "./product-sort-utils";

const SLACK_WEBHOOK_REGEX = /^https:\/\/hooks\.slack\.com\/services\/[A-Za-z0-9/_-]+$/;

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
  msrp: z.coerce.number().min(0).optional(),
  imageUrl: z.string().url("Must be a valid URL").optional().or(z.literal("")),
  notes: z.string().optional(),
  isActive: z.boolean().default(true),
  preferredSupplierId: z.string().optional(),
  preferredSupplierName: z.string().optional(),
  preferredSupplierAuto: z.boolean().optional(),
  // Kuji classification fields. "" = no value (treated as null).
  kujiType: z.enum(["", KujiType.PREMADE, KujiType.CUSTOM]).optional(),
  kujiSlackWebhookUrl: z
    .string()
    .optional()
    .refine(
      (v) => !v || SLACK_WEBHOOK_REGEX.test(v),
      "Must be a valid Slack webhook URL",
    ),
  // Packs per sealed box. Only meaningful for products in the TCG category tree.
  packsPerBox: z.coerce.number().int().min(1, "Must be 1 or greater").optional(),
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
  const { toast } = useToast();
  const { user } = useAuth();
  const { canViewCosts, can } = usePermissions();
  const createMutation = useCreateProductMutation();
  const updateMutation = useUpdateProductMutation();
  const deleteMutation = useDeleteProductMutation();
  const imageUpload = useImageUpload(initialProduct?.imageUrl);
  const { reset: resetImage } = imageUpload;
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const { data: categories, isLoading: categoriesLoading } = useCategories();
  const [addCategoryOpen, setAddCategoryOpen] = useState(false);
  const [addSubcategoryOpen, setAddSubcategoryOpen] = useState(false);
  const [manageCategoriesOpen, setManageCategoriesOpen] = useState(false);
  // Kuji detection and inline prizes state
  const [isKujiCategory, setIsKujiCategory] = useState(false);
  const [pendingPrizes, setPendingPrizes] = useState<PendingPrize[]>([]);
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
  /** "pack" = stored value is the typed value; "box" = stored value is typed * packsPerBox. */
  const [initialStockUnit, setInitialStockUnit] = useState<"pack" | "box">("pack");
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
      msrp: undefined,
      imageUrl: "",
      notes: "",
      isActive: true,
      kujiType: "",
      kujiSlackWebhookUrl: "",
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
          msrp: initialProduct.msrp ?? undefined,
          imageUrl: initialProduct.imageUrl ?? "",
          notes: initialProduct.notes ?? "",
          isActive: initialProduct.isActive ?? true,
          preferredSupplierId: initialProduct.preferredSupplierId ?? "",
          preferredSupplierName: initialProduct.preferredSupplierName ?? "",
          preferredSupplierAuto: initialProduct.preferredSupplierAuto ?? undefined,
          kujiType: initialProduct.kujiType ?? "",
          kujiSlackWebhookUrl: initialProduct.kujiSlackWebhookUrl ?? "",
          packsPerBox: initialProduct.packsPerBox ?? undefined,
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
          msrp: initialProduct.msrp ?? undefined,
          imageUrl: initialProduct.imageUrl ?? "",
          notes: initialProduct.notes ?? "",
          isActive: initialProduct.isActive ?? true,
          preferredSupplierId: initialProduct.preferredSupplierId ?? "",
          preferredSupplierName: initialProduct.preferredSupplierName ?? "",
          preferredSupplierAuto: initialProduct.preferredSupplierAuto ?? undefined,
          kujiType: initialProduct.kujiType ?? "",
          kujiSlackWebhookUrl: initialProduct.kujiSlackWebhookUrl ?? "",
          packsPerBox: initialProduct.packsPerBox ?? undefined,
        });
      }
      resetImage(initialProduct.imageUrl);
    } else {
      setRootCategoryId("");
      setSubcategoryId("");
      form.reset();
      resetImage();
    }

    // Always reset initial stock fields and pending prizes when dialog opens/closes
    setInitialStockEnabled(false);
    setInitialStockLocation(NOT_ASSIGNED_LOCATION);
    setInitialStockQty("");
    setInitialStockQtyError("");
    setInitialStockUnit("pack");
    setLocationError("");
    setPendingPrizes([]);
  }, [open, initialProduct, form, resetImage]);

  // Update categoryId based on subcategory selection
  useEffect(() => {
    if (subcategoryId) {
      form.setValue("categoryId", subcategoryId, { shouldValidate: true });
    } else if (rootCategoryId) {
      form.setValue("categoryId", rootCategoryId, { shouldValidate: true });
    }
  }, [subcategoryId, rootCategoryId, form]);

  // Detect Kuji category and reset prizes when category changes
  useEffect(() => {
    if (!categories) return;
    const selectedCategory = categories.find((c) => c.id === rootCategoryId);
    const isKuji =
      selectedCategory?.name.toLowerCase() === "kuji" ||
      selectedCategory?.slug?.toLowerCase() === "kuji";
    setIsKujiCategory(isKuji);
    if (!isKuji) {
      setPendingPrizes([]);
    }
  }, [rootCategoryId, categories]);

  // Custom kuji has no prize children — clear any pending prizes when toggling to CUSTOM.
  const watchedKujiTypeForReset = form.watch("kujiType");
  useEffect(() => {
    if (watchedKujiTypeForReset === KujiType.CUSTOM) {
      setPendingPrizes([]);
    }
  }, [watchedKujiTypeForReset]);

  // Compute the set of category IDs that belong to the Kuji category tree.
  const kujiCategoryIds = useMemo(
    () => buildKujiCategoryIds(categories ?? []),
    [categories],
  );

  // Categories that use the box/pack toggle (TCG tree: Pokemon, One Piece, etc.).
  const packCategoryIds = useMemo(
    () => buildPackCategoryIds(categories ?? []),
    [categories],
  );

  // Show kuji-type field only on root products whose selected category sits in the Kuji tree.
  const isRootProduct = !parentId && !initialProduct?.parentId;
  const selectedCategoryId = form.watch("categoryId");
  const showKujiTypeField =
    isRootProduct && !!selectedCategoryId && kujiCategoryIds.has(selectedCategoryId);
  // Show packsPerBox field for products in the TCG tree (root or child — packs apply to leaf SKUs).
  const showPacksPerBoxField = !!selectedCategoryId && packCategoryIds.has(selectedCategoryId);
  const watchedKujiType = form.watch("kujiType");

  // CUSTOM kuji parents are templates with their own dedicated tab and lifecycle.
  // After creation, the structural fields (category, supplier, SKU, costs, kuji type)
  // are locked — only descriptive fields (name, image, notes, slack webhook) remain editable.
  const isEditingCustomKuji =
    !!initialProduct && initialProduct.kujiType === KujiType.CUSTOM;

  const isSaving =
    createMutation.isPending ||
    updateMutation.isPending ||
    imageUpload.isUploading ||
    isAddingStock;

  async function onSubmit(values: FormValues) {
    // Validate initial stock BEFORE creating product to avoid partial state
    if (!initialProduct && initialStockEnabled) {
      if (initialStockQty === "") {
        setInitialStockQtyError("Quantity is required");
        return;
      }
      if (!initialStockLocation.locationType || !initialStockLocation.locationId) {
        setLocationError("Location is required");
        return;
      }
    }

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
      unitCost: canViewCosts ? values.unitCost : undefined,
      msrp: canViewCosts ? values.msrp : undefined,
      imageUrl,
      notes: values.notes || undefined,
      isActive: values.isActive,
      preferredSupplierId: values.preferredSupplierId || undefined,
      preferredSupplierAuto: values.preferredSupplierAuto,
    };

    // Thread kuji fields only for root products in the Kuji category tree.
    if (showKujiTypeField) {
      payload.kujiType = values.kujiType ? (values.kujiType as KujiType) : null;
      payload.kujiSlackWebhookUrl =
        values.kujiType === KujiType.CUSTOM
          ? (values.kujiSlackWebhookUrl?.trim() || null)
          : null;
    }

    // Thread packsPerBox only for products in the TCG tree. Outside the tree, send null
    // explicitly so a category change can clear a previously-set value.
    payload.packsPerBox = showPacksPerBoxField
      ? (values.packsPerBox ?? null)
      : null;

    try {
      if (initialProduct) {
        await updateMutation.mutateAsync({ id: initialProduct.id, payload });
        toast({ title: "Product updated", variant: "success" });

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
        toast({ title: "Product created", variant: "success" });
        onProductCreated?.(newProduct);

        // Add initial stock if enabled (validation already done at start of submit)
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
            const formPpbRaw = form.watch("packsPerBox") as unknown;
            const formPpb =
              typeof formPpbRaw === "number"
                ? formPpbRaw
                : typeof formPpbRaw === "string" && formPpbRaw.trim() !== ""
                  ? parseInt(formPpbRaw, 10)
                  : NaN;
            const initialIntakeQty =
              initialStockUnit === "box" &&
              Number.isFinite(formPpb) &&
              formPpb > 1 &&
              typeof initialStockQty === "number"
                ? Math.floor(initialStockQty / formPpb)
                : undefined;
            await createInventory(
              initialStockLocation.locationType,
              initialStockLocation.locationId,
              {
                itemId: newProduct.id,
                quantity: initialStockQty,
                actorId,
                intakeUnit: initialStockUnit === "box" ? "box" : undefined,
                intakeQty: initialIntakeQty,
              },
            );
            toast({ title: "Initial stock added", variant: "success" });
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

        // Create pending prizes for Kuji products
        if (pendingPrizes.length > 0 && !parentId) {
          let prizesFailed = 0;
          for (const prize of pendingPrizes) {
            try {
              const prizePayload = {
                parentId: newProduct.id,
                categoryId: values.categoryId,
                letter: prize.letter,
                templateQuantity: prize.templateQuantity,
                name: `Prize ${prize.letter}`,
                initialStock: prize.quantity > 0 ? prize.quantity : undefined,
              };
              await createMutation.mutateAsync(prizePayload);
            } catch (err) {
              prizesFailed++;
              console.error("Failed to create prize:", prize.letter, err);
            }
          }

          if (prizesFailed > 0) {
            toast({
              title: "Some prizes not added",
              description: `${prizesFailed} prize(s) failed to create.`,
              variant: "destructive",
            });
          } else {
            toast({ title: `${pendingPrizes.length} prize(s) added`, variant: "success" });
          }
        }
      }
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Something went wrong";
      toast({ title: "Save failed", description: message });
    }
  }

  const handleDeleteCustomKuji = () => {
    if (!initialProduct) return;
    deleteMutation.mutate(
      { id: initialProduct.id },
      {
        onSuccess: () => {
          toast({ title: "Custom kuji deleted", variant: "success" });
          setDeleteDialogOpen(false);
          onOpenChange(false);
        },
        onError: (err) => {
          const message =
            err instanceof Error ? err.message : "Something went wrong";
          toast({
            title: "Delete failed",
            description: message,
            variant: "destructive",
          });
        },
      },
    );
  };

  const canDeleteCustomKuji =
    isEditingCustomKuji && can(Permission.PRODUCTS_DELETE);

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

              {!isEditingCustomKuji && (
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

                  {/* Hide subcategory for Kuji products */}
                  {!isKujiCategory && (
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
                  )}
                </div>
              )}

              {/* Preferred Supplier - hide for child products and CUSTOM kuji parents */}
              {!parentId && !isEditingCustomKuji && watchedKujiType !== KujiType.CUSTOM && (
                <div className="grid gap-2">
                  <Label>
                    Preferred Supplier{" "}
                    <span className="text-muted-foreground font-normal">(optional)</span>
                    {form.watch("preferredSupplierAuto") && (
                      <span className="ml-2 text-xs text-muted-foreground">(auto)</span>
                    )}
                  </Label>
                  <SupplierAutocomplete
                    value={form.watch("preferredSupplierId") || null}
                    displayValue={form.watch("preferredSupplierName") || null}
                    onChange={(supplierId, displayName) => {
                      form.setValue("preferredSupplierId", supplierId ?? "");
                      form.setValue("preferredSupplierName", displayName ?? "");
                      // Manual selection sets auto to false
                      form.setValue("preferredSupplierAuto", false);
                    }}
                    placeholder="Select preferred supplier..."
                  />
                  {/* Show auto-suggestion when manually assigned */}
                  {form.watch("preferredSupplierAuto") === false && (
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      {initialProduct?.lastDeliveredSupplierName ? (
                        <>
                          <span>Auto would use: {initialProduct.lastDeliveredSupplierName}</span>
                          <Button
                            type="button"
                            variant="link"
                            size="sm"
                            className="h-auto p-0 text-xs"
                            onClick={() => {
                              form.setValue("preferredSupplierId", initialProduct.lastDeliveredSupplierId ?? "");
                              form.setValue("preferredSupplierName", initialProduct.lastDeliveredSupplierName ?? "");
                              form.setValue("preferredSupplierAuto", true);
                            }}
                          >
                            [Use Auto]
                          </Button>
                        </>
                      ) : (
                        <>
                          <span>Supplier was manually set</span>
                          <Button
                            type="button"
                            variant="link"
                            size="sm"
                            className="h-auto p-0 text-xs"
                            onClick={() => {
                              form.setValue("preferredSupplierId", "");
                              form.setValue("preferredSupplierName", "");
                              form.setValue("preferredSupplierAuto", true);
                            }}
                          >
                            [Enable Auto]
                          </Button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              )}

              {!isEditingCustomKuji && watchedKujiType !== KujiType.CUSTOM && (
                <div className={canViewCosts ? "grid grid-cols-2 gap-4" : ""}>
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

                  {canViewCosts && (
                    <div className="space-y-4">
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

                      <div className="grid gap-2">
                        <Label htmlFor="msrp">
                          MSRP{" "}
                          <span className="text-muted-foreground font-normal">
                            (optional)
                          </span>
                        </Label>
                        <Input
                          id="msrp"
                          type="number"
                          step="0.01"
                          placeholder="0.00"
                          {...form.register("msrp")}
                        />
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Kuji classification — root products in the Kuji category tree.
                  For CUSTOM kuji edit, the type itself is locked but the Slack webhook stays editable. */}
              {showKujiTypeField && !isEditingCustomKuji && (
                <div className="grid gap-2">
                  <Label htmlFor="kuji-type">Kuji Type</Label>
                  <Select
                    value={watchedKujiType ? watchedKujiType : "__none__"}
                    onValueChange={(v) =>
                      form.setValue(
                        "kujiType",
                        v === "__none__"
                          ? ""
                          : (v as KujiType.PREMADE | KujiType.CUSTOM),
                        { shouldDirty: true },
                      )
                    }
                    disabled={isSaving}
                  >
                    <SelectTrigger id="kuji-type">
                      <SelectValue placeholder="—" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__">— None —</SelectItem>
                      <SelectItem value={KujiType.PREMADE}>Premade</SelectItem>
                      <SelectItem value={KujiType.CUSTOM}>Custom</SelectItem>
                    </SelectContent>
                  </Select>

                  {watchedKujiType === KujiType.CUSTOM && (
                    <div className="grid gap-1 mt-2">
                      <Label htmlFor="kuji-slack-webhook">
                        Slack Webhook URL{" "}
                        <span className="text-muted-foreground font-normal">
                          (optional)
                        </span>
                      </Label>
                      <Input
                        id="kuji-slack-webhook"
                        placeholder="https://hooks.slack.com/services/..."
                        {...form.register("kujiSlackWebhookUrl")}
                        disabled={isSaving}
                      />
                      {form.formState.errors.kujiSlackWebhookUrl?.message ? (
                        <p className="text-xs text-destructive">
                          {form.formState.errors.kujiSlackWebhookUrl.message}
                        </p>
                      ) : null}
                    </div>
                  )}
                </div>
              )}

              {/* Slack webhook stays editable on CUSTOM kuji edit even though the type itself is locked. */}
              {isEditingCustomKuji && (
                <div className="grid gap-1">
                  <Label htmlFor="kuji-slack-webhook">
                    Slack Webhook URL{" "}
                    <span className="text-muted-foreground font-normal">
                      (optional)
                    </span>
                  </Label>
                  <Input
                    id="kuji-slack-webhook"
                    placeholder="https://hooks.slack.com/services/..."
                    {...form.register("kujiSlackWebhookUrl")}
                    disabled={isSaving}
                  />
                  {form.formState.errors.kujiSlackWebhookUrl?.message ? (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.kujiSlackWebhookUrl.message}
                    </p>
                  ) : null}
                </div>
              )}

              {/* Packs per sealed box — for products in the TCG category tree.
                  Optional within the tree (singles/accessories leave it blank). */}
              {showPacksPerBoxField && (
                <div className="grid gap-1">
                  <Label htmlFor="packs-per-box">
                    Packs per box{" "}
                    <span className="text-muted-foreground font-normal">
                      (optional)
                    </span>
                  </Label>
                  <Input
                    id="packs-per-box"
                    type="number"
                    min={1}
                    placeholder="e.g. 36"
                    {...form.register("packsPerBox")}
                    disabled={isSaving}
                  />
                  {form.formState.errors.packsPerBox?.message ? (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.packsPerBox.message}
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      Leave blank for singles, accessories, or non-box-packaged items.
                    </p>
                  )}
                </div>
              )}

              {/* Initial stock — create mode only, not for CUSTOM kuji (managed per-box) */}
              {!initialProduct && watchedKujiType !== KujiType.CUSTOM && (
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
                        <Label htmlFor="initial-stock-qty">{isKujiCategory ? "Sets" : "Quantity"}</Label>
                        {(() => {
                          // Form watch may return the raw HTML-input string ("36") rather than
                          // a number while typing — coerce here so the toggle on QuantityInput
                          // gets a live packsPerBox without waiting for resolver validation.
                          const formPpbRaw = form.watch("packsPerBox") as unknown;
                          const parsed =
                            typeof formPpbRaw === "number"
                              ? formPpbRaw
                              : typeof formPpbRaw === "string" && formPpbRaw.trim() !== ""
                                ? parseInt(formPpbRaw, 10)
                                : NaN;
                          const livePpb =
                            showPacksPerBoxField && Number.isFinite(parsed) && parsed > 1
                              ? parsed
                              : null;
                          return (
                            <QuantityInput
                              value={initialStockQty}
                              onChange={(v) => {
                                setInitialStockQty(v);
                                if (v === "") {
                                  setInitialStockQtyError("Quantity is required");
                                } else {
                                  setInitialStockQtyError("");
                                }
                              }}
                              min={1}
                              disabled={isSaving}
                              packsPerBox={livePpb}
                              onIntakeMetaChange={(meta) => setInitialStockUnit(meta.unit)}
                              layout="stacked"
                            />
                          );
                        })()}
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

              {/* Inline prizes section for Kuji - create mode only.
                  Hidden for CUSTOM kuji: tiers are defined per-box, no prize children. */}
              {!initialProduct && isKujiCategory && watchedKujiType !== KujiType.CUSTOM && (
                <div className="border rounded-lg p-4 space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">Prizes</span>
                    <span className="text-xs text-muted-foreground">
                      {pendingPrizes.length} prize{pendingPrizes.length !== 1 ? "s" : ""}
                    </span>
                  </div>
                  <PrizeTableInline
                    prizes={pendingPrizes}
                    onAddPrize={(prize) => {
                      setPendingPrizes((prev) => [
                        ...prev,
                        { ...prize, tempId: crypto.randomUUID() },
                      ]);
                    }}
                    onDeletePrize={(tempId) => {
                      setPendingPrizes((prev) =>
                        prev.filter((p) => p.tempId !== tempId)
                      );
                    }}
                    disabled={isSaving}
                  />
                </div>
              )}
            </div>

            <DialogFooter className="px-6 py-4 sm:justify-between">
              {canDeleteCustomKuji ? (
                <Button
                  type="button"
                  variant="destructive"
                  onClick={() => setDeleteDialogOpen(true)}
                  disabled={isSaving || deleteMutation.isPending}
                >
                  Delete
                </Button>
              ) : (
                <span />
              )}
              <div className="flex flex-col-reverse sm:flex-row gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="dark:bg-accent/50 dark:hover:bg-accent"
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
              </div>
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

      {canDeleteCustomKuji && initialProduct && (
        <DeleteProductDialog
          open={deleteDialogOpen}
          onOpenChange={setDeleteDialogOpen}
          productName={initialProduct.name}
          isPending={deleteMutation.isPending}
          onDelete={handleDeleteCustomKuji}
          renderTrigger={false}
        />
      )}
    </>
  );
}
