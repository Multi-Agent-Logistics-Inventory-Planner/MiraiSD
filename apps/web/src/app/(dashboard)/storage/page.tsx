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
} from "@/types/api";
import { toStorageLocation } from "@/lib/location-utils";
import { naturalSortCompare } from "@/lib/utils";
import { LocationTabs } from "@/components/locations/location-tabs";
import { LocationTable } from "@/components/locations/location-table";
import { LocationDetailSheet } from "@/components/locations/location-detail-sheet";
import { LocationForm } from "@/components/locations/location-form";
import { NotAssignedTable } from "@/components/locations/not-assigned-table";
import { StoragePagination } from "@/components/locations/storage-pagination";
import { useLocationsWithCounts } from "@/hooks/queries/use-locations-with-counts";
import { useNotAssignedInventory } from "@/hooks/queries/use-not-assigned-inventory";
import {
  useCreateLocationMutation,
  useUpdateLocationMutation,
} from "@/hooks/mutations/use-location-mutations";
import { useToast } from "@/hooks/use-toast";

const PAGE_SIZE = 24;

export default function LocationsPage() {
  const { toast } = useToast();
  const [locationType, setLocationType] = useState<LocationType>(
    LocationType.BOX_BIN,
  );
  const [search, setSearch] = useState("");
  const [locationPage, setLocationPage] = useState(1);
  const [notAssignedPage, setNotAssignedPage] = useState(1);

  const isNotAssigned = locationType === LocationType.NOT_ASSIGNED;

  // Optimized: fetch all locations with counts in a single query
  const locationsQuery = useLocationsWithCounts(locationType);

  // Not-assigned inventory query
  const notAssignedQuery = useNotAssignedInventory();

  const [detailOpen, setDetailOpen] = useState(false);
  const [selected, setSelected] = useState<StorageLocation | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<StorageLocation | null>(null);

  const createMutation = useCreateLocationMutation(locationType);
  const updateMutation = useUpdateLocationMutation(locationType);

  // Filter and sort locations based on search (now using LocationWithCounts directly)
  const filteredLocations = useMemo(() => {
    const q = search.trim().toLowerCase();
    const data = locationsQuery.data ?? [];
    const filtered = q
      ? data.filter((x) => x.locationCode.toLowerCase().includes(q))
      : data;

    return [...filtered].sort((a, b) =>
      naturalSortCompare(a.locationCode, b.locationCode)
    );
  }, [locationsQuery.data, search]);

  // Get paginated locations for the current page
  const paginatedLocations = useMemo(() => {
    const start = (locationPage - 1) * PAGE_SIZE;
    return filteredLocations.slice(start, start + PAGE_SIZE);
  }, [filteredLocations, locationPage]);

  // Filter not-assigned items based on search (by SKU or product name)
  const filteredNotAssigned = useMemo(() => {
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

  // Get paginated not-assigned items for the current page
  const notAssignedItems = useMemo(() => {
    const start = (notAssignedPage - 1) * PAGE_SIZE;
    return filteredNotAssigned.slice(start, start + PAGE_SIZE);
  }, [filteredNotAssigned, notAssignedPage]);

  // Reset pagination when search or tab changes
  const handleSearchChange = (value: string) => {
    setSearch(value);
    setLocationPage(1);
    setNotAssignedPage(1);
  };

  const handleLocationTypeChange = (type: LocationType) => {
    setLocationType(type);
    setLocationPage(1);
    setNotAssignedPage(1);
    setSearch("");
  };

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Storage</h1>
      </div>
      <div className="space-y-3">
        <LocationTabs value={locationType} onValueChange={handleLocationTypeChange} />

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="max-w-sm flex-1">
            <Input
              placeholder={
                isNotAssigned
                  ? "Search by SKU or product name..."
                  : "Search by location code..."
              }
              value={search}
              onChange={(e) => handleSearchChange(e.target.value)}
            />
          </div>
          {!isNotAssigned && (
            <Can permission={Permission.STORAGE_CREATE}>
              <Button
                onClick={() => {
                  setEditing(null);
                  setFormOpen(true);
                }}
                className="text-white bg-[#0b66c2] dark:bg-[#7c3aed] dark:text-foreground"
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
            <>
              <NotAssignedTable
                items={notAssignedItems}
                isLoading={notAssignedQuery.isLoading}
                pageSize={PAGE_SIZE}
              />

              <StoragePagination
                page={notAssignedPage}
                pageSize={PAGE_SIZE}
                totalItems={filteredNotAssigned.length}
                isLoading={notAssignedQuery.isLoading}
                onPageChange={setNotAssignedPage}
              />
            </>
          )}

        </>
      ) : (
        <>
          {locationsQuery.isError ? (
            <Card>
              <CardHeader>
                <CardTitle>Could not load locations</CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-muted-foreground">
                {locationsQuery.error instanceof Error
                  ? locationsQuery.error.message
                  : "Unknown error"}
              </CardContent>
            </Card>
          ) : (
            <>
              <LocationTable
                items={paginatedLocations}
                isLoading={locationsQuery.isLoading}
                onRowClick={(row) => {
                  setSelected(toStorageLocation(row));
                  setDetailOpen(true);
                }}
                pageSize={PAGE_SIZE}
              />

              <StoragePagination
                page={locationPage}
                pageSize={PAGE_SIZE}
                totalItems={filteredLocations.length}
                isLoading={locationsQuery.isLoading}
                onPageChange={setLocationPage}
              />
            </>
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
