"use client";

import { useMemo, useState } from "react";
import { Plus } from "lucide-react";
import { DashboardHeader } from "@/components/dashboard-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { LocationType, StorageLocation } from "@/types/api";
import { LocationTabs } from "@/components/locations/location-tabs";
import { LocationList } from "@/components/locations/location-list";
import { LocationDetailSheet } from "@/components/locations/location-detail-sheet";
import { LocationForm } from "@/components/locations/location-form";
import { useLocationsWithCounts } from "@/hooks/queries/use-locations";
import {
  useCreateLocationMutation,
  useUpdateLocationMutation,
} from "@/hooks/mutations/use-location-mutations";
import { useToast } from "@/hooks/use-toast";

export default function LocationsPage() {
  const { toast } = useToast();
  const [locationType, setLocationType] = useState<LocationType>("BOX_BIN");
  const [search, setSearch] = useState("");

  const list = useLocationsWithCounts(locationType);

  const [detailOpen, setDetailOpen] = useState(false);
  const [selected, setSelected] = useState<StorageLocation | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<StorageLocation | null>(null);

  const createMutation = useCreateLocationMutation(locationType);
  const updateMutation = useUpdateLocationMutation(locationType);

  const items = useMemo(() => {
    const q = search.trim().toLowerCase();
    const data = list.data ?? [];
    if (!q) return data;

    return data.filter((x) => {
      const loc = x.location as any;
      const code =
        loc.boxBinCode ??
        loc.rackCode ??
        loc.cabinetCode ??
        loc.singleClawMachineCode ??
        loc.doubleClawMachineCode ??
        loc.keychainMachineCode ??
        "";
      return code.toLowerCase().includes(q);
    });
  }, [list.data, search]);

  return (
    <div className="flex flex-col">
      <DashboardHeader
        title="Locations"
        description="Manage storage locations and their inventory"
      />

      <main className="flex-1 space-y-6 p-4 md:p-6">
        <div className="space-y-3">
          <LocationTabs value={locationType} onValueChange={setLocationType} />

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="max-w-sm flex-1">
              <Input
                placeholder="Search by location code..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <Button
              onClick={() => {
                setEditing(null);
                setFormOpen(true);
              }}
            >
              <Plus className="mr-2 h-4 w-4" />
              Add Location
            </Button>
          </div>
        </div>

        {list.isError ? (
          <Card>
            <CardHeader>
              <CardTitle>Could not load locations</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {list.error instanceof Error ? list.error.message : "Unknown error"}
            </CardContent>
          </Card>
        ) : null}

        <LocationList
          items={items}
          isLoading={list.isLoading}
          onSelect={(row) => {
            setSelected(row.location);
            setDetailOpen(true);
          }}
        />

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
      </main>
    </div>
  );
}

