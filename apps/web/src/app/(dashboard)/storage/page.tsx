"use client";

import { useMemo, useState } from "react";
import { Plus } from "lucide-react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Can, Permission } from "@/components/rbac";
import {
  LocationType,
  type StorageLocation,
  type BoxBin,
  type Rack,
  type Cabinet,
  type SingleClawMachine,
  type DoubleClawMachine,
  type KeychainMachine,
  type FourCornerMachine,
  type PusherMachine,
  type NotAssignedInventory,
} from "@/types/api";

function getLocationCode(location: StorageLocation): string {
  if ("boxBinCode" in location) return (location as BoxBin).boxBinCode;
  if ("rackCode" in location) return (location as Rack).rackCode;
  if ("cabinetCode" in location) return (location as Cabinet).cabinetCode;
  if ("singleClawMachineCode" in location)
    return (location as SingleClawMachine).singleClawMachineCode;
  if ("doubleClawMachineCode" in location)
    return (location as DoubleClawMachine).doubleClawMachineCode;
  if ("keychainMachineCode" in location)
    return (location as KeychainMachine).keychainMachineCode;
  if ("fourCornerMachineCode" in location)
    return (location as FourCornerMachine).fourCornerMachineCode;
  if ("pusherMachineCode" in location)
    return (location as PusherMachine).pusherMachineCode;
  return "";
}
import { LocationTabs } from "@/components/locations/location-tabs";
import { LocationTable } from "@/components/locations/location-table";
import { LocationDetailSheet } from "@/components/locations/location-detail-sheet";
import { LocationForm } from "@/components/locations/location-form";
import { NotAssignedTable } from "@/components/locations/not-assigned-table";
import { NotAssignedDetailDialog } from "@/components/locations/not-assigned-detail-dialog";
import { useLocationsWithCounts } from "@/hooks/queries/use-locations";
import { useNotAssignedInventory } from "@/hooks/queries/use-not-assigned-inventory";
import {
  useCreateLocationMutation,
  useUpdateLocationMutation,
} from "@/hooks/mutations/use-location-mutations";
import { useToast } from "@/hooks/use-toast";

export default function LocationsPage() {
  const { toast } = useToast();
  const [locationType, setLocationType] = useState<LocationType>(
    LocationType.BOX_BIN,
  );
  const [search, setSearch] = useState("");

  const isNotAssigned = locationType === LocationType.NOT_ASSIGNED;

  // Location-based queries (for all tabs except NOT_ASSIGNED)
  const list = useLocationsWithCounts(locationType);

  // Not-assigned inventory query
  const notAssignedQuery = useNotAssignedInventory();

  const [detailOpen, setDetailOpen] = useState(false);
  const [selected, setSelected] = useState<StorageLocation | null>(null);

  const [notAssignedDetailOpen, setNotAssignedDetailOpen] = useState(false);
  const [selectedNotAssigned, setSelectedNotAssigned] = useState<NotAssignedInventory | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<StorageLocation | null>(null);

  const createMutation = useCreateLocationMutation(locationType);
  const updateMutation = useUpdateLocationMutation(locationType);

  // Filter locations based on search
  const locationItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    const data = list.data ?? [];
    if (!q) return data;

    return data.filter((x) => {
      const code = getLocationCode(x.location);
      return code.toLowerCase().includes(q);
    });
  }, [list.data, search]);

  // Filter not-assigned items based on search (by SKU or product name)
  const notAssignedItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    const data = notAssignedQuery.data ?? [];
    if (!q) return data;

    return data.filter((x) => {
      return (
        x.item.sku.toLowerCase().includes(q) ||
        x.item.name.toLowerCase().includes(q)
      );
    });
  }, [notAssignedQuery.data, search]);

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Storage</h1>
      </div>
        <div className="space-y-3">
          <LocationTabs value={locationType} onValueChange={setLocationType} />

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="max-w-sm flex-1">
              <Input
                placeholder={
                  isNotAssigned
                    ? "Search by SKU or product name..."
                    : "Search by location code..."
                }
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            {!isNotAssigned && (
              <Can permission={Permission.STORAGE_CREATE}>
                <Button
                  onClick={() => {
                    setEditing(null);
                    setFormOpen(true);
                  }}
                >
                  <Plus className="mr-2 h-4 w-4" />
                  Add Location
                </Button>
              </Can>
            )}
          </div>
        </div>

        {isNotAssigned ? (
          <>
            {notAssignedQuery.isError ? (
              <Card>
                <CardHeader>
                  <CardTitle>Could not load unassigned inventory</CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">
                  {notAssignedQuery.error instanceof Error
                    ? notAssignedQuery.error.message
                    : "Unknown error"}
                </CardContent>
              </Card>
            ) : (
              <Card className="py-0">
                <CardContent className="p-0">
                  <NotAssignedTable
                    items={notAssignedItems}
                    isLoading={notAssignedQuery.isLoading}
                    onRowClick={(item) => {
                      setSelectedNotAssigned(item);
                      setNotAssignedDetailOpen(true);
                    }}
                  />
                </CardContent>
              </Card>
            )}

            <NotAssignedDetailDialog
              open={notAssignedDetailOpen}
              onOpenChange={setNotAssignedDetailOpen}
              inventory={selectedNotAssigned}
            />
          </>
        ) : (
          <>
            {list.isError ? (
              <Card>
                <CardHeader>
                  <CardTitle>Could not load locations</CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">
                  {list.error instanceof Error
                    ? list.error.message
                    : "Unknown error"}
                </CardContent>
              </Card>
            ) : (
              <Card className="py-0">
                <CardContent className="p-0">
                  <LocationTable
                    items={locationItems}
                    isLoading={list.isLoading}
                    onRowClick={(row) => {
                      setSelected(row.location);
                      setDetailOpen(true);
                    }}
                  />
                </CardContent>
              </Card>
            )}

            <LocationDetailSheet
              open={detailOpen}
              onOpenChange={setDetailOpen}
              locationType={locationType}
              location={selected}
              onEdit={(loc) => {
                setEditing(loc);
                setFormOpen(true);
              }}
            />

            <LocationForm
              open={formOpen}
              onOpenChange={setFormOpen}
              locationType={locationType}
              initialLocation={editing}
              isSaving={createMutation.isPending || updateMutation.isPending}
              onSubmit={async (payload) => {
                try {
                  if (editing) {
                    await updateMutation.mutateAsync({ id: editing.id, payload });
                    toast({ title: "Location updated" });
                  } else {
                    await createMutation.mutateAsync(payload);
                    toast({ title: "Location created" });
                  }
                } catch (err: unknown) {
                  const msg = err instanceof Error ? err.message : "Save failed";
                  toast({ title: "Save failed", description: msg });
                }
              }}
            />
          </>
        )}
    </div>
  );
}
