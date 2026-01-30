"use client";

import { useEffect, useState, useRef } from "react";
import { z } from "zod";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Check, ChevronsUpDown, Loader2, Plus, Trash2 } from "lucide-react";
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
  CommandSeparator,
} from "@/components/ui/command";
import { cn } from "@/lib/utils";
import { useProducts } from "@/hooks/queries/use-products";
import { useCreateShipmentMutation } from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { ProductForm } from "@/components/products/product-form";
import { ShipmentStatus, type Product, type ShipmentItemRequest } from "@/types/api";

const itemSchema = z.object({
  productId: z.string().min(1, "Product is required"),
  productName: z.string(),
  productSku: z.string(),
  orderedQuantity: z.coerce.number().int().min(1, "Quantity must be at least 1"),
  unitCost: z.coerce.number().min(0).optional(),
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
  const productsQuery = useProducts();
  const products = productsQuery.data ?? [];

  const [productFormOpen, setProductFormOpen] = useState(false);
  const [comboOpenIndex, setComboOpenIndex] = useState<number | null>(null);
  // Track which item index triggered "Create New Product" and previous product count
  const pendingProductSelectIndex = useRef<number | null>(null);
  const previousProductCount = useRef<number>(0);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      shipmentNumber: "",
      supplierName: "",
      orderDate: new Date().toISOString().split("T")[0],
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
        },
      ],
    },
  });

  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "items",
  });

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      form.reset({
        shipmentNumber: generateShipmentNumber(),
        supplierName: "",
        orderDate: new Date().toISOString().split("T")[0],
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
          },
        ],
      });
      pendingProductSelectIndex.current = null;
    }
  }, [open, form]);

  // Auto-select newly created product
  useEffect(() => {
    const itemIndex = pendingProductSelectIndex.current;
    if (
      itemIndex !== null &&
      !productFormOpen &&
      products.length > previousProductCount.current
    ) {
      // Find the newest product (most recently created)
      const newestProduct = products.reduce((newest, current) => {
        if (!newest) return current;
        return new Date(current.createdAt) > new Date(newest.createdAt)
          ? current
          : newest;
      }, products[0]);

      if (newestProduct) {
        handleSelectProduct(itemIndex, newestProduct);
      }
      pendingProductSelectIndex.current = null;
    }
    previousProductCount.current = products.length;
  }, [products, productFormOpen]);

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
    form.setValue(`items.${index}.productSku`, product.sku);
    if (product.unitCost) {
      form.setValue(`items.${index}.unitCost`, product.unitCost);
    }
    setComboOpenIndex(null);
  }

  async function onSubmit(values: FormValues) {
    const items: ShipmentItemRequest[] = values.items.map((item) => ({
      itemId: item.productId,
      orderedQuantity: item.orderedQuantity,
      unitCost: item.unitCost,
    }));

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

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] flex flex-col gap-0 p-0">
          <DialogHeader className="p-6">
            <DialogTitle>Create New Shipment</DialogTitle>
            <DialogDescription>
              Add a new inbound shipment to track incoming inventory.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="flex flex-col flex-1 min-h-0"
          >
            <div className="overflow-y-auto px-6 pb-4 space-y-4">
              {/* Basic Info */}
              <div className="grid grid-cols-2 gap-4">
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

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="orderDate">Order Date</Label>
                  <Input
                    id="orderDate"
                    type="date"
                    {...form.register("orderDate")}
                  />
                  {form.formState.errors.orderDate?.message && (
                    <p className="text-xs text-destructive">
                      {form.formState.errors.orderDate.message}
                    </p>
                  )}
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="expectedDeliveryDate">
                    Expected Delivery Date
                  </Label>
                  <Input
                    id="expectedDeliveryDate"
                    type="date"
                    {...form.register("expectedDeliveryDate")}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="trackingId">Tracking Number (optional)</Label>
                  <Input
                    id="trackingId"
                    placeholder="Enter carrier tracking number..."
                    {...form.register("trackingId")}
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="totalCost">Total Cost ($)</Label>
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
                <div className="flex items-center justify-between">
                  <Label>Items</Label>
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        const newIndex = fields.length;
                        append({
                          productId: "",
                          productName: "",
                          productSku: "",
                          orderedQuantity: 1,
                          unitCost: undefined,
                        });
                        pendingProductSelectIndex.current = newIndex;
                        previousProductCount.current = products.length;
                        setProductFormOpen(true);
                      }}
                    >
                      <Plus className="h-4 w-4 mr-1" />
                      Create Product
                    </Button>
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
                        })
                      }
                    >
                      <Plus className="h-4 w-4 mr-1" />
                      Add Item
                    </Button>
                  </div>
                </div>

                {form.formState.errors.items?.message && (
                  <p className="text-xs text-destructive">
                    {form.formState.errors.items.message}
                  </p>
                )}

                <div className="space-y-3">
                  {fields.map((field, index) => {
                    const selectedProductId = form.watch(
                      `items.${index}.productId`
                    );
                    const selectedProduct = products.find(
                      (p) => p.id === selectedProductId
                    );

                    return (
                      <div
                        key={field.id}
                        className="flex items-start gap-3 p-3 border rounded-lg bg-muted/30"
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
                                    ? `${selectedProduct.name} (${selectedProduct.sku})`
                                    : "Select product..."}
                                  <ChevronsUpDown className="ml-2 h-4 w-4 opacity-50" />
                                </Button>
                              </PopoverTrigger>
                              <PopoverContent
                                className="w-[400px] p-0"
                                align="start"
                              >
                                <Command>
                                  <CommandInput placeholder="Search products..." />
                                  <CommandList>
                                    <CommandEmpty>
                                      No products found.
                                    </CommandEmpty>
                                    <CommandGroup>
                                      {products.map((p) => (
                                        <CommandItem
                                          key={p.id}
                                          value={`${p.name} ${p.sku}`}
                                          onSelect={() =>
                                            handleSelectProduct(index, p)
                                          }
                                        >
                                          <Check
                                            className={cn(
                                              "mr-2 h-4 w-4",
                                              selectedProductId === p.id
                                                ? "opacity-100"
                                                : "opacity-0"
                                            )}
                                          />
                                          <div className="flex flex-col">
                                            <span className="text-sm">
                                              {p.name}
                                            </span>
                                            <span className="text-xs text-muted-foreground">
                                              {p.sku}
                                            </span>
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

                          <div className="grid grid-cols-2 gap-3">
                            <div className="grid gap-2">
                              <Label className="text-xs">Quantity</Label>
                              <Input
                                type="number"
                                min={1}
                                {...form.register(
                                  `items.${index}.orderedQuantity`
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
                              <Label className="text-xs">Unit Cost ($)</Label>
                              <Input
                                type="number"
                                step="0.01"
                                min={0}
                                placeholder="Optional"
                                {...form.register(`items.${index}.unitCost`)}
                              />
                            </div>
                          </div>
                        </div>

                        {fields.length > 1 && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="shrink-0 mt-6"
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

            <DialogFooter className="px-6 py-4 border-t">
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

      <ProductForm
        open={productFormOpen}
        onOpenChange={setProductFormOpen}
        initialProduct={null}
      />
    </>
  );
}
