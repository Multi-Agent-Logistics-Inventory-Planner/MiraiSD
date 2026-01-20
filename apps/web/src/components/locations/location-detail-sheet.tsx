"use client";

import { useMemo, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { LocationType, StorageLocation, Inventory, InventoryRequest } from "@/types/api";
import { cn } from "@/lib/utils";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import {
  useCreateInventoryMutation,
  useDeleteLocationMutation,
} from "@/hooks/mutations/use-location-mutations";
import { AddInventoryDialog } from "@/components/locations/add-inventory-dialog";

function getLocationCode(locationType: LocationType, loc: StorageLocation): string {
  switch (locationType) {
    case "BOX_BIN":
      return (loc as any).boxBinCode;
    case "RACK":
      return (loc as any).rackCode;
    case "CABINET":
      return (loc as any).cabinetCode;
    case "SINGLE_CLAW_MACHINE":
      return (loc as any).singleClawMachineCode;
    case "DOUBLE_CLAW_MACHINE":
      return (loc as any).doubleClawMachineCode;
    case "KEYCHAIN_MACHINE":
      return (loc as any).keychainMachineCode;
    default:
      return loc.id;
  }
}

interface LocationDetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  locationType: LocationType;
  location: StorageLocation | null;
  onEdit: (location: StorageLocation) => void;
}

export function LocationDetailSheet({
  open,
  onOpenChange,
  locationType,
  location,
  onEdit,
}: LocationDetailSheetProps) {
  const locationId = location?.id;
  const inventoryQuery = useLocationInventory(locationType, locationId);
  const createInventory = useCreateInventoryMutation(locationType, locationId ?? "");
  const deleteLocation = useDeleteLocationMutation(locationType);

  const [addOpen, setAddOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const code = location ? getLocationCode(locationType, location) : "";

  const totalQty = useMemo(() => {
    const inv = (inventoryQuery.data ?? []) as Inventory[];
    return inv.reduce((sum, r) => sum + (r.quantity ?? 0), 0);
  }, [inventoryQuery.data]);

  async function handleAddInventory(payload: InventoryRequest) {
    await createInventory.mutateAsync(payload);
  }

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="sm:max-w-2xl">
          <SheetHeader>
            <SheetTitle className="flex items-center justify-between gap-3">
              <span>{code || "Location"}</span>
              {location ? (
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setAddOpen(true)}
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    Add Inventory
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onEdit(location)}
                  >
                    <Pencil className="mr-2 h-4 w-4" />
                    Edit
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-destructive"
                    onClick={() => setDeleteOpen(true)}
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </Button>
                </div>
              ) : null}
            </SheetTitle>
            <SheetDescription>
              {locationType} • {totalQty} total units
            </SheetDescription>
          </SheetHeader>

          <div className="mt-4 space-y-4">
            {inventoryQuery.isError ? (
              <Card className="p-4 text-sm text-muted-foreground">
                Failed to load inventory.
              </Card>
            ) : null}

            <Card className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>SKU</TableHead>
                    <TableHead>Product</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead className="text-right">Qty</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {inventoryQuery.isLoading ? (
                    Array.from({ length: 6 }).map((_, i) => (
                      <TableRow key={i}>
                        <TableCell>
                          <Skeleton className="h-4 w-20" />
                        </TableCell>
                        <TableCell>
                          <Skeleton className="h-4 w-48" />
                        </TableCell>
                        <TableCell>
                          <Skeleton className="h-4 w-24" />
                        </TableCell>
                        <TableCell className="text-right">
                          <Skeleton className="ml-auto h-4 w-10" />
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (inventoryQuery.data as Inventory[] | undefined)?.length ? (
                    (inventoryQuery.data as Inventory[]).map((inv) => (
                      <TableRow key={inv.id}>
                        <TableCell className="font-mono text-sm">
                          {inv.item.sku}
                        </TableCell>
                        <TableCell className="font-medium">{inv.item.name}</TableCell>
                        <TableCell>
                          <Badge
                            variant="outline"
                            className={cn("text-xs")}
                          >
                            {inv.item.category}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">{inv.quantity}</TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell
                        colSpan={4}
                        className="py-8 text-center text-sm text-muted-foreground"
                      >
                        No inventory in this location yet.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Card>
          </div>
        </SheetContent>
      </Sheet>

      {locationId ? (
        <AddInventoryDialog
          open={addOpen}
          onOpenChange={setAddOpen}
          locationType={locationType}
          locationId={locationId}
          isSaving={createInventory.isPending}
          onSubmit={handleAddInventory}
        />
      ) : null}

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete location?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete <span className="font-medium">{code}</span>.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={async () => {
                if (!locationId) return;
                await deleteLocation.mutateAsync({ id: locationId });
                onOpenChange(false);
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

