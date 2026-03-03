"use client";

import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Check, ChevronsUpDown, X } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  LocationType,
  LOCATION_TYPE_LABELS,
  Product,
  LocationWithCounts,
  SetMachineDisplayBatchRequest,
} from "@/types/api";

// Machine location types only
const MACHINE_LOCATION_TYPES = [
  LocationType.SINGLE_CLAW_MACHINE,
  LocationType.DOUBLE_CLAW_MACHINE,
  LocationType.KEYCHAIN_MACHINE,
  LocationType.FOUR_CORNER_MACHINE,
  LocationType.PUSHER_MACHINE,
] as const;

const schema = z.object({
  locationType: z.nativeEnum(LocationType),
  machineId: z.string().min(1, "Machine is required"),
  productIds: z.array(z.string()).min(1, "At least one product is required"),
});

type FormValues = z.infer<typeof schema>;

interface SetDisplayDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: SetMachineDisplayBatchRequest) => Promise<void>;
  products: Product[];
  locations: LocationWithCounts[];
  actorId?: string;
  isSubmitting?: boolean;
  // Pre-fill values for editing
  initialLocationType?: LocationType;
  initialMachineId?: string;
}

export function SetDisplayDialog({
  open,
  onOpenChange,
  onSubmit,
  products,
  locations,
  actorId,
  isSubmitting = false,
  initialLocationType,
  initialMachineId,
}: SetDisplayDialogProps) {
  const [productOpen, setProductOpen] = useState(false);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      locationType: initialLocationType ?? LocationType.SINGLE_CLAW_MACHINE,
      machineId: initialMachineId ?? "",
      productIds: [],
    },
  });

  const selectedLocationType = form.watch("locationType");
  const selectedProductIds = form.watch("productIds");

  // Filter machines by selected location type
  const filteredMachines = locations.filter(
    (loc) => loc.locationType === selectedLocationType
  );

  // Reset machine selection when location type changes
  useEffect(() => {
    if (!initialMachineId) {
      form.setValue("machineId", "");
    }
  }, [selectedLocationType, form, initialMachineId]);

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      form.reset({
        locationType: initialLocationType ?? LocationType.SINGLE_CLAW_MACHINE,
        machineId: initialMachineId ?? "",
        productIds: [],
      });
    }
  }, [open, form, initialLocationType, initialMachineId]);

  const handleSubmit = async (values: FormValues) => {
    await onSubmit({
      locationType: values.locationType,
      machineId: values.machineId,
      productIds: values.productIds,
      actorId,
    });
    onOpenChange(false);
  };

  const handleProductToggle = (productId: string) => {
    const current = form.getValues("productIds");
    if (current.includes(productId)) {
      form.setValue(
        "productIds",
        current.filter((id) => id !== productId),
        { shouldValidate: true }
      );
    } else {
      form.setValue("productIds", [...current, productId], {
        shouldValidate: true,
      });
    }
  };

  const handleRemoveProduct = (productId: string) => {
    const current = form.getValues("productIds");
    form.setValue(
      "productIds",
      current.filter((id) => id !== productId),
      { shouldValidate: true }
    );
  };

  const selectedProducts = products.filter((p) =>
    selectedProductIds.includes(p.id)
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Set Machine Display</DialogTitle>
          <DialogDescription>
            Select a machine and one or more products to display.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
          {/* Machine Type */}
          <div className="space-y-2">
            <Label htmlFor="locationType">Machine Type</Label>
            <Select
              value={form.watch("locationType")}
              onValueChange={(v) =>
                form.setValue("locationType", v as LocationType)
              }
              disabled={!!initialLocationType}
            >
              <SelectTrigger id="locationType">
                <SelectValue placeholder="Select type" />
              </SelectTrigger>
              <SelectContent>
                {MACHINE_LOCATION_TYPES.map((type) => (
                  <SelectItem key={type} value={type}>
                    {LOCATION_TYPE_LABELS[type]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Machine */}
          <div className="space-y-2">
            <Label htmlFor="machineId">Machine</Label>
            <Select
              value={form.watch("machineId")}
              onValueChange={(v) => form.setValue("machineId", v)}
              disabled={!!initialMachineId}
            >
              <SelectTrigger id="machineId">
                <SelectValue placeholder="Select machine" />
              </SelectTrigger>
              <SelectContent>
                {filteredMachines.length === 0 ? (
                  <SelectItem value="__none__" disabled>
                    No machines available
                  </SelectItem>
                ) : (
                  filteredMachines.map((machine) => (
                    <SelectItem key={machine.id} value={machine.id}>
                      {machine.locationCode}
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
            {form.formState.errors.machineId && (
              <p className="text-sm text-destructive">
                {form.formState.errors.machineId.message}
              </p>
            )}
          </div>

          {/* Products (Multi-select Combobox) */}
          <div className="space-y-2">
            <Label>Products</Label>
            <Popover open={productOpen} onOpenChange={setProductOpen}>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  aria-expanded={productOpen}
                  className="w-full justify-between"
                >
                  {selectedProducts.length > 0 ? (
                    <span className="truncate">
                      {selectedProducts.length} product(s) selected
                    </span>
                  ) : (
                    <span className="text-muted-foreground">
                      Select products...
                    </span>
                  )}
                  <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-[350px] p-0" align="start">
                <Command>
                  <CommandInput placeholder="Search products..." />
                  <CommandList>
                    <CommandEmpty>No products found.</CommandEmpty>
                    <CommandGroup>
                      {products.map((product) => (
                        <CommandItem
                          key={product.id}
                          value={`${product.name} ${product.sku}`}
                          onSelect={() => handleProductToggle(product.id)}
                        >
                          <Check
                            className={cn(
                              "mr-2 h-4 w-4",
                              selectedProductIds.includes(product.id)
                                ? "opacity-100"
                                : "opacity-0"
                            )}
                          />
                          <div className="flex flex-col">
                            <span>{product.name}</span>
                            <span className="text-xs text-muted-foreground">
                              {product.sku}
                            </span>
                          </div>
                        </CommandItem>
                      ))}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
            {/* Selected products badges */}
            {selectedProducts.length > 0 && (
              <div className="flex flex-wrap gap-1 mt-2">
                {selectedProducts.map((product) => (
                  <Badge
                    key={product.id}
                    variant="secondary"
                    className="text-xs"
                  >
                    {product.name}
                    <button
                      type="button"
                      onClick={() => handleRemoveProduct(product.id)}
                      className="ml-1 hover:text-destructive"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))}
              </div>
            )}
            {form.formState.errors.productIds && (
              <p className="text-sm text-destructive">
                {form.formState.errors.productIds.message}
              </p>
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Setting..." : "Set Display"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
