"use client";

import { useEffect, useMemo, useState } from "react";
import { format } from "date-fns";
import { z } from "zod";
import {
  Controller,
  useForm,
  useFieldArray,
  type UseFormReturn,
} from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueries } from "@tanstack/react-query";
import {
  ArrowLeft,
  Calendar as CalendarIcon,
  ChevronsUpDown,
  Info,
  Loader2,
  Package,
  Plus,
  Trash2,
} from "lucide-react";
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
import { QuantityInput } from "@/components/ui/quantity-input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn, prizeLetterDisplay, sortPrizes } from "@/lib/utils";
import { Calendar } from "@/components/ui/calendar";
import { useProducts } from "@/hooks/queries/use-products";
import { getProductChildren } from "@/lib/api/products";
import { ProductForm } from "@/components/products/product-form";
import { SelectShipmentProductDialog } from "@/components/shipments/select-shipment-product-dialog";
import { SupplierAutocomplete } from "@/components/suppliers";
import {
  useCreateShipmentMutation,
  useUpdateShipmentMutation,
} from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { usePermissions } from "@/hooks/use-permissions";
import {
  ShipmentStatus,
  type ProductListItem,
  type Shipment,
  type ShipmentItemRequest,
} from "@/types/api";

const itemSchema = z.object({
  productId: z.string().min(1, "Product is required"),
  productName: z.string(),
  productSku: z.string(),
  orderedQuantity: z.coerce
    .number()
    .int()
    .min(0, "Quantity must be 0 or more")
    .optional(),
  unitCost: z.coerce.number().min(0).optional(),
  numberOfSets: z.coerce.number().int().min(0).optional(),
  prizeQuantities: z.record(z.string(), z.coerce.number().int().min(0)).optional(),
});

const schema = z.object({
  shipmentNumber: z.string().min(1, "Shipment Name is required"),
  supplierId: z.string().optional(),
  supplierName: z.string().optional(),
  orderDate: z.string().min(1, "Order date is required"),
  expectedDeliveryDate: z.string().optional(),
  trackingId: z.string().optional(),
  totalCost: z.coerce.number().min(0).optional(),
  notes: z.string().optional(),
  items: z.array(itemSchema).min(1, "At least one item is required"),
});

type FormValues = z.infer<typeof schema>;

type ActiveKey = "details" | number;

interface ShipmentCreateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialShipment?: Shipment | null;
}

export function ShipmentCreateDialog({
  open,
  onOpenChange,
  initialShipment,
}: ShipmentCreateDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const { canViewCosts } = usePermissions();
  const createMutation = useCreateShipmentMutation();
  const updateMutation = useUpdateShipmentMutation();
  const productsQuery = useProducts({ rootOnly: true, excludeCustomKuji: true });
  const products = productsQuery.data ?? [];

  const isEditMode = !!initialShipment;

  const [productDialogIndex, setProductDialogIndex] = useState<number | null>(
    null,
  );
  const [selectedProductIds, setSelectedProductIds] = useState<
    Record<number, string>
  >({});
  const [addProductOpen, setAddProductOpen] = useState(false);
  const [activeKey, setActiveKey] = useState<ActiveKey>("details");
  const [mobileShowMenu, setMobileShowMenu] = useState(true);

  function selectKey(key: ActiveKey) {
    setActiveKey(key);
    setMobileShowMenu(false);
  }

  const excludeProductIds = Object.values(selectedProductIds).filter(Boolean);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      shipmentNumber: "",
      supplierName: "",
      orderDate: format(new Date(), "yyyy-MM-dd"),
      expectedDeliveryDate: "",
      trackingId: "",
      totalCost: undefined,
      notes: "",
      items: [
        {
          productId: "",
          productName: "",
          productSku: "",
          orderedQuantity: undefined,
          unitCost: undefined,
          numberOfSets: undefined,
          prizeQuantities: undefined,
        },
      ],
    },
  });

  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "items",
  });

  const items = form.watch("items");

  const rowHasReceipts: Record<number, boolean> = (() => {
    if (!initialShipment) return {};
    const rootItems = initialShipment.items.filter((i) => !i.item.parentId);
    const map: Record<number, boolean> = {};
    rootItems.forEach((it, index) => {
      const total =
        (it.receivedQuantity ?? 0) +
        (it.damagedQuantity ?? 0) +
        (it.displayQuantity ?? 0) +
        (it.shopQuantity ?? 0);
      map[index] = total > 0;
    });
    return map;
  })();

  const selectedKujiIds = useMemo(() => {
    return Object.values(selectedProductIds)
      .filter((id): id is string => !!id)
      .filter((id) => products.find((p) => p.id === id)?.hasChildren);
  }, [selectedProductIds, products]);

  const childrenQueries = useQueries({
    queries: selectedKujiIds.map((id) => ({
      queryKey: ["products", id, "children"],
      queryFn: () => getProductChildren(id),
    })),
  });

  const childrenByProductId = useMemo(() => {
    const result: Record<string, ProductListItem[]> = {};
    selectedKujiIds.forEach((id, i) => {
      const data = childrenQueries[i]?.data;
      if (data?.length) result[id] = data;
    });
    return result;
  }, [selectedKujiIds, childrenQueries]);

  function isLoadingChildrenForProduct(productId: string): boolean {
    const idx = selectedKujiIds.indexOf(productId);
    return idx !== -1 && childrenQueries[idx]?.isLoading === true;
  }

  useEffect(() => {
    if (!open) return;
    if (initialShipment) {
      const rootItems = initialShipment.items.filter((i) => !i.item.parentId);
      const prizesByParentId: Record<string, typeof initialShipment.items> = {};
      initialShipment.items.forEach((i) => {
        const pid = i.item.parentId;
        if (pid) {
          if (!prizesByParentId[pid]) prizesByParentId[pid] = [];
          prizesByParentId[pid].push(i);
        }
      });

      const formItems = rootItems.map((item) => {
        const prizes = prizesByParentId[item.item.id] ?? [];
        const prizeQuantities: Record<string, number> = {};
        prizes.forEach((p) => {
          prizeQuantities[p.item.id] = p.orderedQuantity;
        });
        return {
          productId: item.item.id,
          productName: item.item.name,
          productSku: item.item.sku ?? "",
          orderedQuantity: item.orderedQuantity,
          unitCost: item.unitCost,
          numberOfSets: undefined,
          prizeQuantities:
            Object.keys(prizeQuantities).length > 0 ? prizeQuantities : undefined,
        };
      });

      const productIds: Record<number, string> = {};
      rootItems.forEach((item, index) => {
        productIds[index] = item.item.id;
      });
      setSelectedProductIds(productIds);

      form.reset({
        shipmentNumber: initialShipment.shipmentNumber,
        supplierId: initialShipment.supplierId ?? "",
        supplierName: initialShipment.supplierName ?? "",
        orderDate: initialShipment.orderDate,
        expectedDeliveryDate: initialShipment.expectedDeliveryDate ?? "",
        trackingId: initialShipment.trackingId ?? "",
        totalCost: initialShipment.totalCost,
        notes: initialShipment.notes ?? "",
        items:
          formItems.length > 0
            ? formItems
            : [
                {
                  productId: "",
                  productName: "",
                  productSku: "",
                  orderedQuantity: undefined,
                  unitCost: undefined,
                  prizeQuantities: undefined,
                },
              ],
      });
    } else {
      form.reset({
        shipmentNumber: "",
        supplierId: "",
        supplierName: "",
        orderDate: format(new Date(), "yyyy-MM-dd"),
        expectedDeliveryDate: "",
        trackingId: "",
        totalCost: undefined,
        notes: "",
        items: [
          {
            productId: "",
            productName: "",
            productSku: "",
            orderedQuantity: undefined,
            unitCost: undefined,
            numberOfSets: undefined,
            prizeQuantities: undefined,
          },
        ],
      });
      setSelectedProductIds({});
    }
    setActiveKey("details");
    setMobileShowMenu(true);
  }, [open, form, initialShipment]);

  // When children load for a Kuji, init prizeQuantities for that item row.
  useEffect(() => {
    items?.forEach((item, index) => {
      const pid = item.productId;
      if (!pid) return;
      const children = childrenByProductId[pid];
      if (!children?.length) return;
      const current = item.prizeQuantities ?? {};
      const next: Record<string, number> = {};
      children.forEach((c) => {
        next[c.id] = current[c.id] ?? 0;
      });
      if (
        children.some((c) => current[c.id] === undefined) ||
        Object.keys(next).length !== Object.keys(current).length
      ) {
        form.setValue(`items.${index}.prizeQuantities`, next, {
          shouldValidate: false,
        });
      }
    });
  }, [childrenByProductId, form, items]);

  function handleSelectProduct(index: number, product: ProductListItem) {
    const currentItem = form.getValues(`items.${index}`);
    form.setValue(
      `items.${index}`,
      {
        ...currentItem,
        productId: product.id,
        productName: product.name,
        productSku: product.sku ?? "",
        numberOfSets: undefined,
        prizeQuantities: undefined,
        unitCost: product.unitCost ?? currentItem.unitCost,
      },
      { shouldValidate: false },
    );
    setSelectedProductIds((prev) => ({ ...prev, [index]: product.id }));
  }

  function handleAddItem() {
    append({
      productId: "",
      productName: "",
      productSku: "",
      orderedQuantity: undefined,
      unitCost: undefined,
      numberOfSets: undefined,
      prizeQuantities: undefined,
    });
    selectKey(fields.length); // new index after append
  }

  function handleRemoveItem(index: number) {
    if (fields.length <= 1) return;
    remove(index);
    setSelectedProductIds((prev) => {
      const next: Record<number, string> = {};
      Object.keys(prev).forEach((k) => {
        const i = Number(k);
        if (i < index) next[i] = prev[i];
        else if (i > index) next[i - 1] = prev[i];
      });
      return next;
    });
    if (activeKey === index) {
      const newCount = fields.length - 1;
      if (newCount === 0) setActiveKey("details");
      else setActiveKey(Math.max(0, index - 1));
    } else if (typeof activeKey === "number" && activeKey > index) {
      setActiveKey(activeKey - 1);
    }
  }

  async function onSubmit(values: FormValues) {
    const shipmentItems: ShipmentItemRequest[] = [];
    for (const item of values.items) {
      if (!item.productId) continue;
      const parentQty = item.orderedQuantity ?? 0;
      if (parentQty > 0) {
        shipmentItems.push({
          itemId: item.productId,
          orderedQuantity: parentQty,
          unitCost: item.unitCost,
        });
      }
      const prizeQuantities = item.prizeQuantities ?? {};
      for (const [prizeId, qty] of Object.entries(prizeQuantities)) {
        if (qty > 0) {
          shipmentItems.push({
            itemId: prizeId,
            orderedQuantity: qty,
            unitCost: undefined,
          });
        }
      }
    }
    if (shipmentItems.length === 0) {
      toast({
        title: "Error",
        description: "Add at least one item with quantity.",
      });
      return;
    }

    try {
      const payload = {
        shipmentNumber: values.shipmentNumber,
        supplierId: values.supplierId || undefined,
        supplierName: values.supplierName || undefined,
        status: initialShipment?.status ?? ShipmentStatus.PENDING,
        orderDate: values.orderDate,
        expectedDeliveryDate: values.expectedDeliveryDate || undefined,
        trackingId: values.trackingId || undefined,
        totalCost: values.totalCost,
        notes: values.notes || undefined,
        createdBy: user?.personId,
        items: shipmentItems,
      };

      if (isEditMode && initialShipment) {
        await updateMutation.mutateAsync({ id: initialShipment.id, payload });
        toast({ title: "Shipment updated successfully", variant: "success" });
      } else {
        await createMutation.mutateAsync(payload);
        toast({ title: "Shipment created successfully", variant: "success" });
      }
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : `Failed to ${isEditMode ? "update" : "create"} shipment`;
      toast({ title: "Error", description: message });
    }
  }

  // Surface validation errors by jumping to the offending entry.
  function onInvalid(errs: Record<string, unknown>) {
    const headerKeys = [
      "shipmentNumber",
      "supplierId",
      "supplierName",
      "orderDate",
      "expectedDeliveryDate",
      "trackingId",
      "totalCost",
      "notes",
    ];
    if (headerKeys.some((k) => k in errs)) {
      selectKey("details");
      return;
    }
    if (errs.items && typeof errs.items === "object") {
      const itemErrs = errs.items as Record<string, unknown>;
      for (const k of Object.keys(itemErrs)) {
        const idx = Number(k);
        if (!Number.isNaN(idx)) {
          selectKey(idx);
          return;
        }
      }
    }
  }

  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <>
      <ProductForm
        open={addProductOpen}
        onOpenChange={setAddProductOpen}
        onProductCreated={(product) => {
          append({
            productId: product.id,
            productName: product.name,
            productSku: product.sku ?? "",
            orderedQuantity: undefined,
            unitCost: product.unitCost ?? undefined,
            numberOfSets: undefined,
            prizeQuantities: undefined,
          });
          setSelectedProductIds((prev) => ({
            ...prev,
            [fields.length]: product.id,
          }));
          selectKey(fields.length);
        }}
      />
      <SelectShipmentProductDialog
        open={productDialogIndex !== null}
        onOpenChange={(isOpen) => {
          if (!isOpen) setProductDialogIndex(null);
        }}
        excludeProductIds={excludeProductIds}
        onSelect={(product) => {
          if (productDialogIndex !== null) {
            handleSelectProduct(productDialogIndex, product);
            setProductDialogIndex(null);
          }
        }}
      />
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="w-[calc(100%-2rem)] sm:max-w-3xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
          <DialogHeader className="px-6 py-4 border-b">
            <DialogTitle>
              {isEditMode ? "Edit Shipment" : "Create New Shipment"}
            </DialogTitle>
            <DialogDescription>
              {isEditMode
                ? "Update the shipment details below."
                : "Add a new inbound shipment to track incoming inventory."}
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={form.handleSubmit(onSubmit, onInvalid)}
            className="flex flex-col flex-1 min-h-0"
          >
            <div className="flex flex-1 min-h-0 overflow-hidden">
              <aside
                className={cn(
                  "w-full sm:w-[220px] shrink-0 bg-muted/40 sm:border-r flex flex-col overflow-hidden",
                  !mobileShowMenu && "hidden sm:flex",
                )}
              >
                <div className="px-2 pt-3">
                  <DetailsSidebarItem
                    active={activeKey === "details"}
                    onSelect={() => selectKey("details")}
                  />
                </div>

                <div className="flex items-center px-3 pt-3 pb-1.5">
                  <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                    Products
                  </span>
                </div>

                <div className="flex-1 overflow-y-auto px-2 pb-2">
                  {fields.map((field, index) => {
                    const item = items?.[index];
                    const productId = item?.productId ?? "";
                    const isKuji = !!childrenByProductId[productId]?.length;
                    return (
                      <ProductSidebarItem
                        key={field.id}
                        index={index}
                        productName={item?.productName ?? ""}
                        orderedQuantity={item?.orderedQuantity ?? 0}
                        isKuji={isKuji}
                        active={activeKey === index}
                        canRemove={fields.length > 1}
                        disabled={isSaving}
                        onSelect={() => selectKey(index)}
                        onRemove={() => handleRemoveItem(index)}
                      />
                    );
                  })}
                  {form.formState.errors.items?.message && (
                    <p className="text-xs text-destructive px-2 py-2">
                      {form.formState.errors.items.message}
                    </p>
                  )}
                </div>

                <div className="border-t flex flex-col">
                  <button
                    type="button"
                    onClick={() => setAddProductOpen(true)}
                    disabled={isSaving}
                    className="flex items-center justify-center gap-1.5 px-3 py-2.5 text-sm text-muted-foreground hover:text-foreground hover:bg-muted/60 disabled:opacity-50 transition-colors border-b"
                  >
                    <Plus className="h-4 w-4" />
                    Add Product
                  </button>
                  <button
                    type="button"
                    onClick={handleAddItem}
                    disabled={isSaving}
                    className="flex items-center justify-center gap-1.5 px-3 py-2.5 text-sm text-muted-foreground hover:text-foreground hover:bg-muted/60 disabled:opacity-50 transition-colors"
                  >
                    <Plus className="h-4 w-4" />
                    Add Item
                  </button>
                </div>
              </aside>

              <div
                className={cn(
                  "flex-1 overflow-y-auto min-w-0",
                  mobileShowMenu && "hidden sm:block",
                )}
              >
                <div className="sm:hidden border-b">
                  <button
                    type="button"
                    onClick={() => setMobileShowMenu(true)}
                    className="flex items-center gap-1.5 px-4 py-2.5 text-sm text-muted-foreground hover:text-foreground"
                  >
                    <ArrowLeft className="h-4 w-4" />
                    Back
                  </button>
                </div>
                {activeKey === "details" ? (
                  <ShipmentDetailsPanel
                    form={form}
                    canViewCosts={canViewCosts}
                    isEditMode={isEditMode}
                  />
                ) : typeof activeKey === "number" && fields[activeKey] ? (
                  <ShipmentItemPanel
                    key={fields[activeKey].id}
                    form={form}
                    index={activeKey}
                    products={products}
                    productsLoading={productsQuery.isLoading}
                    rowHasReceipts={rowHasReceipts[activeKey] ?? false}
                    childrenByProductId={childrenByProductId}
                    isLoadingChildrenForProduct={isLoadingChildrenForProduct}
                    onOpenProductPicker={() => setProductDialogIndex(activeKey)}
                  />
                ) : null}
              </div>
            </div>

            <DialogFooter className="px-6 py-4 border-t">
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
                className="text-white bg-brand-primary hover:bg-brand-primary-hover"
              >
                {isSaving ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : null}
                {isEditMode ? "Update Shipment" : "Create Shipment"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

interface DetailsSidebarItemProps {
  readonly active: boolean;
  readonly onSelect: () => void;
}

function DetailsSidebarItem({ active, onSelect }: DetailsSidebarItemProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelect();
        }
      }}
      className={cn(
        "group flex items-center gap-2 px-2 py-2 rounded-md cursor-pointer border border-transparent transition-colors",
        active
          ? "bg-background border-border shadow-sm"
          : "hover:bg-muted/60",
      )}
    >
      <div
        className={cn(
          "w-7 h-7 rounded-md flex items-center justify-center shrink-0",
          active
            ? "bg-muted text-foreground"
            : "bg-muted/60 text-muted-foreground",
        )}
      >
        <Info className="h-3.5 w-3.5" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-xs font-medium truncate">Shipment details</div>
        <div className="text-[11px] text-muted-foreground truncate">
          Name, dates, supplier
        </div>
      </div>
    </div>
  );
}

interface ProductSidebarItemProps {
  readonly index: number;
  readonly productName: string;
  readonly orderedQuantity: number;
  readonly isKuji: boolean;
  readonly active: boolean;
  readonly canRemove: boolean;
  readonly disabled: boolean;
  readonly onSelect: () => void;
  readonly onRemove: () => void;
}

function ProductSidebarItem({
  index,
  productName,
  orderedQuantity,
  isKuji,
  active,
  canRemove,
  disabled,
  onSelect,
  onRemove,
}: ProductSidebarItemProps) {
  const subtitle = orderedQuantity
    ? `${orderedQuantity} ${isKuji ? "sets" : "units"}`
    : "No quantity set";

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelect();
        }
      }}
      className={cn(
        "group flex items-center gap-2 px-2 py-2 rounded-md cursor-pointer mb-0.5 border border-transparent transition-colors",
        active
          ? "bg-background border-border shadow-sm"
          : "hover:bg-muted/60",
      )}
    >
      <div
        className={cn(
          "w-7 h-7 rounded-md flex items-center justify-center text-xs font-semibold shrink-0",
          active
            ? "bg-muted text-foreground"
            : "bg-muted/60 text-muted-foreground",
        )}
      >
        {index + 1}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-xs font-medium truncate">
          {productName || "Select product…"}
        </div>
        <div className="text-[11px] text-muted-foreground truncate">
          {subtitle}
        </div>
      </div>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onRemove();
        }}
        disabled={disabled || !canRemove}
        aria-label={`Remove product ${index + 1}`}
        className={cn(
          "shrink-0 p-1 rounded text-muted-foreground hover:text-destructive hover:bg-destructive/10 opacity-0 group-hover:opacity-100 transition-opacity",
          (!canRemove || disabled) && "pointer-events-none opacity-0",
        )}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

interface ShipmentDetailsPanelProps {
  readonly form: UseFormReturn<FormValues>;
  readonly canViewCosts: boolean;
  readonly isEditMode: boolean;
}

function ShipmentDetailsPanel({
  form,
  canViewCosts,
  isEditMode,
}: ShipmentDetailsPanelProps) {
  const orderDateValue = form.watch("orderDate");
  const expectedDeliveryDateValue = form.watch("expectedDeliveryDate");

  function toDateString(date: Date): string {
    const y = date.getUTCFullYear();
    const m = String(date.getUTCMonth() + 1).padStart(2, "0");
    const d = String(date.getUTCDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }

  return (
    <div className="p-5 space-y-4">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        Shipment details
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="grid gap-1.5">
          <Label htmlFor="shipmentNumber" className="text-xs">
            Shipment Name
          </Label>
          <Input
            id="shipmentNumber"
            placeholder="Enter shipment name..."
            readOnly={isEditMode}
            className={isEditMode ? "bg-muted" : undefined}
            {...form.register("shipmentNumber")}
          />
          {form.formState.errors.shipmentNumber?.message && (
            <p className="text-xs text-destructive">
              {form.formState.errors.shipmentNumber.message}
            </p>
          )}
        </div>
        <div className="grid gap-1.5">
          <Label className="text-xs">Supplier</Label>
          <SupplierAutocomplete
            value={form.watch("supplierId") || null}
            displayValue={form.watch("supplierName") || null}
            onChange={(supplierId, displayName) => {
              form.setValue("supplierId", supplierId ?? "");
              form.setValue("supplierName", displayName ?? "");
            }}
            placeholder="Select supplier (optional)"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="grid gap-1.5">
          <Label className="text-xs">Order Date</Label>
          <Popover>
            <PopoverTrigger asChild>
              <Button
                type="button"
                variant="outline"
                className={cn(
                  "w-full justify-start text-left font-normal",
                  !orderDateValue && "text-muted-foreground",
                )}
              >
                {orderDateValue ? (
                  format(new Date(orderDateValue + "T00:00:00"), "MM/dd/yyyy")
                ) : (
                  <span>mm/dd/yyyy</span>
                )}
                <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="single"
                selected={
                  orderDateValue
                    ? new Date(orderDateValue + "T00:00:00")
                    : undefined
                }
                onSelect={(date) =>
                  form.setValue(
                    "orderDate",
                    date ? toDateString(date) : "",
                    { shouldValidate: true },
                  )
                }
              />
            </PopoverContent>
          </Popover>
          {form.formState.errors.orderDate?.message && (
            <p className="text-xs text-destructive">
              {form.formState.errors.orderDate.message}
            </p>
          )}
        </div>
        <div className="grid gap-1.5">
          <Label className="text-xs">Expected Delivery Date</Label>
          <Popover>
            <PopoverTrigger asChild>
              <Button
                type="button"
                variant="outline"
                className={cn(
                  "w-full justify-start text-left font-normal",
                  !expectedDeliveryDateValue && "text-muted-foreground",
                )}
              >
                {expectedDeliveryDateValue ? (
                  format(
                    new Date(expectedDeliveryDateValue + "T00:00:00"),
                    "MM/dd/yyyy",
                  )
                ) : (
                  <span>mm/dd/yyyy</span>
                )}
                <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="single"
                selected={
                  expectedDeliveryDateValue
                    ? new Date(expectedDeliveryDateValue + "T00:00:00")
                    : undefined
                }
                onSelect={(date) =>
                  form.setValue(
                    "expectedDeliveryDate",
                    date ? toDateString(date) : "",
                    { shouldValidate: true },
                  )
                }
              />
            </PopoverContent>
          </Popover>
        </div>
      </div>

      <div
        className={
          canViewCosts ? "grid grid-cols-1 sm:grid-cols-2 gap-4" : ""
        }
      >
        <div className="grid gap-1.5">
          <Label htmlFor="trackingId" className="text-xs">
            Tracking Number{" "}
            <span className="text-muted-foreground font-normal">(optional)</span>
          </Label>
          <Input
            id="trackingId"
            placeholder="Enter carrier tracking number..."
            {...form.register("trackingId")}
          />
        </div>
        {canViewCosts && (
          <div className="grid gap-1.5">
            <Label htmlFor="totalCost" className="text-xs">
              Total Cost
            </Label>
            <Input
              id="totalCost"
              type="number"
              step="0.01"
              min={0}
              placeholder="Optional"
              {...form.register("totalCost")}
            />
          </div>
        )}
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="notes" className="text-xs">
          Notes{" "}
          <span className="text-muted-foreground font-normal">(optional)</span>
        </Label>
        <Input
          id="notes"
          placeholder="Any additional notes..."
          {...form.register("notes")}
        />
      </div>
    </div>
  );
}

interface ShipmentItemPanelProps {
  readonly form: UseFormReturn<FormValues>;
  readonly index: number;
  readonly products: ProductListItem[];
  readonly productsLoading: boolean;
  readonly rowHasReceipts: boolean;
  readonly childrenByProductId: Record<string, ProductListItem[]>;
  readonly isLoadingChildrenForProduct: (productId: string) => boolean;
  readonly onOpenProductPicker: () => void;
}

function ShipmentItemPanel({
  form,
  index,
  products,
  productsLoading,
  rowHasReceipts,
  childrenByProductId,
  isLoadingChildrenForProduct,
  onOpenProductPicker,
}: ShipmentItemPanelProps) {
  const selectedProductId = form.watch(`items.${index}.productId`);
  const selectedProduct = products.find((p) => p.id === selectedProductId);
  const hasKujiChildren = !!childrenByProductId[selectedProductId]?.length;
  const useQuantityInput = !hasKujiChildren;

  return (
    <div className="p-5 space-y-4">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        <Package className="h-3.5 w-3.5" />
        Product
        <span className="bg-muted rounded px-2 py-0.5 text-[11px] text-foreground">
          {index + 1}
        </span>
      </div>

      <div className="grid gap-1.5">
        <Label className="text-xs">Product</Label>
        <Button
          type="button"
          variant="outline"
          className="justify-between w-full"
          disabled={productsLoading}
          onClick={onOpenProductPicker}
        >
          {selectedProduct
            ? selectedProduct.sku
              ? `${selectedProduct.name} (${selectedProduct.sku})`
              : selectedProduct.name
            : "Select product..."}
          <ChevronsUpDown className="ml-2 h-4 w-4 opacity-50" />
        </Button>
        {form.formState.errors.items?.[index]?.productId?.message && (
          <p className="text-xs text-destructive">
            {form.formState.errors.items[index]?.productId?.message}
          </p>
        )}
      </div>

      <div className="grid gap-1.5">
        <Label className="text-xs">
          {hasKujiChildren ? "Total Kuji (sets)" : "Quantity"}
        </Label>
        {useQuantityInput ? (
          <Controller
            control={form.control}
            name={`items.${index}.orderedQuantity`}
            render={({ field }) => (
              <QuantityInput
                value={field.value ?? ""}
                onChange={(v) => field.onChange(v === "" ? undefined : v)}
                disabled={rowHasReceipts}
                packsPerBox={selectedProduct?.packsPerBox}
                layout="stacked"
              />
            )}
          />
        ) : (
          <Input
            type="number"
            min={0}
            disabled={rowHasReceipts}
            title={
              rowHasReceipts
                ? "Undo this item's receipts before changing the ordered quantity."
                : undefined
            }
            {...form.register(`items.${index}.orderedQuantity`)}
          />
        )}
        {rowHasReceipts && (
          <p className="text-[11px] text-muted-foreground">
            Undo this item&apos;s receipts to change the ordered quantity.
          </p>
        )}
        {form.formState.errors.items?.[index]?.orderedQuantity?.message && (
          <p className="text-xs text-destructive">
            {form.formState.errors.items[index]?.orderedQuantity?.message}
          </p>
        )}
      </div>

      {isLoadingChildrenForProduct(selectedProductId) ? (
        <div className="space-y-2 rounded-md border p-3 bg-muted/20">
          <div className="flex items-center gap-3 pb-2 border-b">
            <Skeleton className="h-4 w-10" />
            <Skeleton className="h-8 w-20" />
          </div>
          <Skeleton className="h-4 w-32 mt-2" />
          <div className="space-y-2">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        </div>
      ) : hasKujiChildren ? (
        <div className="space-y-2 rounded-md border p-3 bg-muted/20">
          <div className="flex items-center gap-3 pb-2 border-b">
            <Label className="text-xs font-medium">Sets:</Label>
            <Input
              type="number"
              min={0}
              className="w-20 h-8"
              placeholder="0"
              {...form.register(`items.${index}.numberOfSets`)}
              onChange={(e) => {
                const sets = parseInt(e.target.value, 10) || 0;
                form.setValue(`items.${index}.numberOfSets`, sets);
                form.setValue(`items.${index}.orderedQuantity`, sets);
                const children = childrenByProductId[selectedProductId] ?? [];
                const newPrizeQtys: Record<string, number> = {};
                children.forEach((prize) => {
                  const templateQty = prize.templateQuantity ?? 0;
                  newPrizeQtys[prize.id] = templateQty * sets;
                });
                form.setValue(
                  `items.${index}.prizeQuantities`,
                  newPrizeQtys,
                );
              }}
            />
            <span className="text-xs text-muted-foreground">
              (auto-fills prize quantities)
            </span>
          </div>
          <Label className="text-xs">Prize quantities (editable)</Label>
          <div className="grid gap-2">
            {sortPrizes(childrenByProductId[selectedProductId] ?? []).map(
              (prize) => (
                <div key={prize.id} className="flex items-center gap-2">
                  <span
                    className="text-sm font-medium min-w-0 truncate max-w-[4rem]"
                    title={prize.letter ?? prize.name}
                  >
                    {prizeLetterDisplay(prize.letter) ||
                      prize.name.slice(0, 1)}
                  </span>
                  {prize.templateQuantity && (
                    <span className="text-xs text-muted-foreground">
                      ({prize.templateQuantity}/set)
                    </span>
                  )}
                  <span className="text-sm text-muted-foreground flex-1 truncate">
                    {prize.name}
                  </span>
                  <Input
                    type="number"
                    min={0}
                    className="w-20"
                    {...form.register(
                      `items.${index}.prizeQuantities.${prize.id}`,
                      {
                        setValueAs: (v) =>
                          Math.max(0, parseInt(String(v), 10) || 0),
                      },
                    )}
                  />
                </div>
              ),
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
