"use client";

import { useEffect, useState, useMemo } from "react";
import { format } from "date-fns";
import { z } from "zod";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueries } from "@tanstack/react-query";
import {
  Calendar as CalendarIcon,
  Check,
  ChevronsUpDown,
  Loader2,
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
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { cn } from "@/lib/utils";
import { Calendar } from "@/components/ui/calendar";
import { useProducts } from "@/hooks/queries/use-products";
import { getProductChildren } from "@/lib/api/products";
import { useCreateShipmentMutation } from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import {
  ShipmentStatus,
  type Product,
  type ShipmentItemRequest,
  type ProductSummary,
} from "@/types/api";

const itemSchema = z.object({
  productId: z.string().min(1, "Product is required"),
  productName: z.string(),
  productSku: z.string(),
  orderedQuantity: z.coerce
    .number()
    .int()
    .min(0, "Quantity must be 0 or more"),
  unitCost: z.coerce.number().min(0).optional(),
  /** When Kuji is selected: quantity per prize (prizeId -> quantity) */
  prizeQuantities: z.record(z.string(), z.coerce.number().int().min(0)).optional(),
});

const schema = z.object({
  shipmentNumber: z.string().min(1, "Shipment number is required"),
  supplierName: z.string().optional(),
  orderDate: z.string().min(1, "Order date is required"),
  expectedDeliveryDate: z.string().optional(),
  trackingId: z.string().optional(),
  totalCost: z.coerce.number().min(0).optional(),
  notes: z.string().optional(),
  items: z.array(itemSchema).min(1, "At least one item is required"),
});

type FormValues = z.infer<typeof schema>;

interface ShipmentCreateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ShipmentCreateDialog({
  open,
  onOpenChange,
}: ShipmentCreateDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const createMutation = useCreateShipmentMutation();
  const productsQuery = useProducts({ rootOnly: true });
  const products = productsQuery.data ?? [];

  const [comboOpenIndex, setComboOpenIndex] = useState<number | null>(null);

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
          orderedQuantity: 1,
          unitCost: undefined,
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
  const selectedKujiIds = useMemo(
    () =>
      (items ?? [])
        .map((i) => i.productId)
        .filter((id): id is string => !!id),
    [items]
  );
  const childrenQueries = useQueries({
    queries: selectedKujiIds.map((id) => ({
      queryKey: ["products", id, "children"],
      queryFn: () => getProductChildren(id),
    })),
  });
  const childrenByProductId = useMemo(() => {
    const map: Record<string, ProductSummary[]> = {};
    selectedKujiIds.forEach((id, i) => {
      const data = childrenQueries[i]?.data;
      if (data?.length) map[id] = data;
    });
    return map;
  }, [selectedKujiIds, childrenQueries]);

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      form.reset({
        shipmentNumber: generateShipmentNumber(),
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
            orderedQuantity: 1,
            unitCost: undefined,
            prizeQuantities: undefined,
          },
        ],
      });
    }
  }, [open, form]);

  // When children load for a Kuji, init prizeQuantities for that item row
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

  function generateShipmentNumber() {
    const date = new Date();
    const year = date.getFullYear().toString().slice(-2);
    const month = (date.getMonth() + 1).toString().padStart(2, "0");
    const day = date.getDate().toString().padStart(2, "0");
    const random = Math.floor(Math.random() * 1000)
      .toString()
      .padStart(3, "0");
    return `SHP-${year}${month}${day}-${random}`;
  }

  function handleSelectProduct(index: number, product: Product) {
    form.setValue(`items.${index}.productId`, product.id);
    form.setValue(`items.${index}.productName`, product.name);
    form.setValue(`items.${index}.productSku`, product.sku ?? "");
    form.setValue(`items.${index}.prizeQuantities`, undefined);
    if (product.unitCost) {
      form.setValue(`items.${index}.unitCost`, product.unitCost);
    }
    setComboOpenIndex(null);
  }

  async function onSubmit(values: FormValues) {
    const items: ShipmentItemRequest[] = [];
    for (const item of values.items) {
      if (!item.productId) continue;
      const parentQty = item.orderedQuantity ?? 0;
      if (parentQty > 0) {
        items.push({
          itemId: item.productId,
          orderedQuantity: parentQty,
          unitCost: item.unitCost,
        });
      }
      const prizeQuantities = item.prizeQuantities ?? {};
      for (const [prizeId, qty] of Object.entries(prizeQuantities)) {
        if (qty > 0) {
          items.push({
            itemId: prizeId,
            orderedQuantity: qty,
            unitCost: undefined,
          });
        }
      }
    }
    if (items.length === 0) {
      toast({ title: "Error", description: "Add at least one item with quantity." });
      return;
    }

    try {
      await createMutation.mutateAsync({
        shipmentNumber: values.shipmentNumber,
        supplierName: values.supplierName || undefined,
        status: ShipmentStatus.PENDING,
        orderDate: values.orderDate,
        expectedDeliveryDate: values.expectedDeliveryDate || undefined,
        trackingId: values.trackingId || undefined,
        totalCost: values.totalCost,
        notes: values.notes || undefined,
        createdBy: user?.personId,
        items,
      });
      toast({ title: "Shipment created successfully" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to create shipment";
      toast({ title: "Error", description: message });
    }
  }

  const isSaving = createMutation.isPending;
  const orderDateValue = form.watch("orderDate");
  const expectedDeliveryDateValue = form.watch("expectedDeliveryDate");

  // react-day-picker v9 passes UTC midnight dates to onSelect,
  // so we must read UTC parts to get the correct calendar date.
  function toDateString(date: Date): string {
    const y = date.getUTCFullYear();
    const m = String(date.getUTCMonth() + 1).padStart(2, "0");
    const d = String(date.getUTCDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] flex flex-col gap-0 p-0">
          <DialogHeader className="p-4 sm:p-6">
            <DialogTitle>Create New Shipment</DialogTitle>
            <DialogDescription>
              Add a new inbound shipment to track incoming inventory.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="flex flex-col flex-1 min-h-0"
          >
            <div className="overflow-y-auto px-4 sm:px-6 pb-4 space-y-4">
              {/* Basic Info */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="shipmentNumber">Shipment Number</Label>
                  <Input
                    id="shipmentNumber"
                    {...form.register("shipmentNumber")}
                  />
                  {form.formState.errors.shipmentNumber?.message && (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.shipmentNumber.message}
                    </p>
                  )}
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="supplierName">Supplier Name</Label>
                  <Input
                    id="supplierName"
                    placeholder="Optional"
                    {...form.register("supplierName")}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label>Order Date</Label>
                  <Popover>
                    <PopoverTrigger asChild>
                      <Button
                        variant="outline"
                        className={cn(
                          "w-full justify-start text-left font-normal",
                          !orderDateValue && "text-muted-foreground",
                        )}
                      >
                        {orderDateValue ? (
                          format(
                            new Date(orderDateValue + "T00:00:00"),
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
                <div className="grid gap-2">
                  <Label>Expected Delivery Date</Label>
                  <Popover>
                    <PopoverTrigger asChild>
                      <Button
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

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="trackingId">Tracking Number (optional)</Label>
                  <Input
                    id="trackingId"
                    placeholder="Enter carrier tracking number..."
                    {...form.register("trackingId")}
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="totalCost">Total Cost</Label>
                  <Input
                    id="totalCost"
                    type="number"
                    step="0.01"
                    min={0}
                    placeholder="Optional"
                    {...form.register("totalCost")}
                  />
                </div>
              </div>

              {/* Items */}
              <div className="space-y-3">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                  <Label>Items</Label>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      append({
                        productId: "",
                        productName: "",
                        productSku: "",
                        orderedQuantity: 1,
                        unitCost: undefined,
                        prizeQuantities: undefined,
                      })
                    }
                  >
                    <Plus className="h-4 w-4 mr-1" />
                    Add Item
                  </Button>
                </div>

                {form.formState.errors.items?.message && (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.items.message}
                  </p>
                )}

                <div className="space-y-3">
                  {fields.map((field, index) => {
                    const selectedProductId = form.watch(
                      `items.${index}.productId`,
                    );
                    const selectedProduct = products.find(
                      (p) => p.id === selectedProductId,
                    );

                    return (
                      <div
                        key={field.id}
                        className="flex flex-col sm:flex-row sm:items-start gap-3 p-3 border rounded-lg bg-muted/30"
                      >
                        <div className="flex-1 space-y-3">
                          {/* Product Selector */}
                          <div className="grid gap-2">
                            <Label className="text-xs">Product</Label>
                            <Popover
                              open={comboOpenIndex === index}
                              onOpenChange={(isOpen) =>
                                setComboOpenIndex(isOpen ? index : null)
                              }
                            >
                              <PopoverTrigger asChild>
                                <Button
                                  variant="outline"
                                  role="combobox"
                                  className="justify-between w-full"
                                  disabled={productsQuery.isLoading}
                                >
                                  {selectedProduct
                                    ? selectedProduct.sku
                                      ? `${selectedProduct.name} (${selectedProduct.sku})`
                                      : selectedProduct.name
                                    : "Select product..."}
                                  <ChevronsUpDown className="ml-2 h-4 w-4 opacity-50" />
                                </Button>
                              </PopoverTrigger>
                              <PopoverContent
                                className="w-[calc(100vw-3rem)] sm:w-[400px] p-0"
                                align="start"
                              >
                                <Command>
                                  <CommandInput placeholder="Search products..." />
                                  <CommandList>
                                    <CommandEmpty>
                                      No products found
                                    </CommandEmpty>
                                    <CommandGroup>
                                      {products.map((p) => (
                                        <CommandItem
                                          key={p.id}
                                          value={`${p.name} ${p.sku ?? ""}`.trim()}
                                          onSelect={() =>
                                            handleSelectProduct(index, p)
                                          }
                                        >
                                          <Check
                                            className={cn(
                                              "mr-2 h-4 w-4",
                                              selectedProductId === p.id
                                                ? "opacity-100"
                                                : "opacity-0",
                                            )}
                                          />
                                          <div className="flex flex-col">
                                            <span className="text-sm">
                                              {p.name}
                                            </span>
                                            {p.sku ? (
                                              <span className="text-xs text-muted-foreground">
                                                {p.sku}
                                              </span>
                                            ) : null}
                                          </div>
                                        </CommandItem>
                                      ))}
                                    </CommandGroup>
                                  </CommandList>
                                </Command>
                              </PopoverContent>
                            </Popover>
                            {form.formState.errors.items?.[index]?.productId
                              ?.message && (
                              <p className="text-xs text-destructive">
                                {
                                  form.formState.errors.items[index]?.productId
                                    ?.message
                                }
                              </p>
                            )}
                          </div>

                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="grid gap-2">
                              <Label className="text-xs">
                                {childrenByProductId[selectedProductId]?.length
                                  ? "Total Kuji (sets)"
                                  : "Quantity"}
                              </Label>
                              <Input
                                type="number"
                                min={0}
                                {...form.register(
                                  `items.${index}.orderedQuantity`,
                                )}
                              />
                              {form.formState.errors.items?.[index]
                                ?.orderedQuantity?.message && (
                                <p className="text-xs text-destructive">
                                  {
                                    form.formState.errors.items[index]
                                      ?.orderedQuantity?.message
                                  }
                                </p>
                              )}
                            </div>
                            <div className="grid gap-2">
                              <Label className="text-xs">Unit Cost</Label>
                              <Input
                                type="number"
                                step="0.01"
                                min={0}
                                placeholder="Optional"
                                {...form.register(`items.${index}.unitCost`)}
                              />
                            </div>
                          </div>

                          {childrenByProductId[selectedProductId]?.length ? (
                            <div className="space-y-2 rounded-md border p-3 bg-muted/20">
                              <Label className="text-xs">
                                Prize quantities (incoming per prize)
                              </Label>
                              <div className="grid gap-2">
                                {childrenByProductId[
                                  selectedProductId
                                ]?.map((prize) => (
                                  <div
                                    key={prize.id}
                                    className="flex items-center gap-2"
                                  >
                                    <span className="text-sm font-medium w-8">
                                      {prize.letter ?? prize.name.slice(0, 1)}
                                    </span>
                                    <span className="text-sm text-muted-foreground flex-1 truncate">
                                      {prize.name}
                                    </span>
                                    <Input
                                      type="number"
                                      min={0}
                                      className="w-20"
                                      {...form.register(
                                        `items.${index}.prizeQuantities.${prize.id}`,
                                        { setValueAs: (v) =>
                                          Math.max(0, parseInt(String(v), 10) || 0),
                                        }
                                      )}
                                    />
                                  </div>
                                ))}
                              </div>
                            </div>
                          ) : null}
                        </div>

                        {fields.length > 1 && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="shrink-0 self-end sm:mt-6"
                            onClick={() => remove(index)}
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Notes */}
              <div className="grid gap-2">
                <Label htmlFor="notes">Notes (optional)</Label>
                <Input
                  id="notes"
                  placeholder="Any additional notes..."
                  {...form.register("notes")}
                />
              </div>
            </div>

            <DialogFooter className="px-4 py-3 sm:px-6 sm:py-4 border-t">
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
                Create Shipment
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
