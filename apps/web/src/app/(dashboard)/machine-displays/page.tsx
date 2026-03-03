"use client";

import { useState, useMemo } from "react";
import { Plus, AlertTriangle } from "lucide-react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import {
  MachineDisplayFilters,
  MachineDisplayFiltersState,
  MachineDisplayTable,
  SetDisplayDialog,
  MachineDisplayDetailModal,
  DEFAULT_MACHINE_DISPLAY_FILTERS,
} from "@/components/machine-displays";
import {
  useActiveDisplays,
  useStaleDisplays,
  useActiveDisplaysForMachine,
} from "@/hooks/queries/use-machine-displays";
import {
  useSetMachineDisplayMutation,
  useSetMachineDisplayBatchMutation,
  useClearDisplayByIdMutation,
} from "@/hooks/mutations/use-machine-display-mutations";
import { useProducts } from "@/hooks/queries/use-products";
import { useLocationsWithCounts } from "@/hooks/queries/use-locations-with-counts";
import { useAuth } from "@/hooks/use-auth";
import { MachineDisplay, LocationType, SetMachineDisplayBatchRequest } from "@/types/api";

export default function MachineDisplaysPage() {
  const { user } = useAuth();
  const { toast } = useToast();
  const [filters, setFilters] = useState<MachineDisplayFiltersState>(
    DEFAULT_MACHINE_DISPLAY_FILTERS
  );
  const [dialogOpen, setDialogOpen] = useState(false);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [selectedDisplay, setSelectedDisplay] = useState<MachineDisplay | null>(
    null
  );

  // Queries
  const { data: allDisplays, isLoading: isLoadingAll } = useActiveDisplays();
  const { data: staleDisplays } = useStaleDisplays();
  const { data: products = [] } = useProducts();
  const { data: locations = [] } = useLocationsWithCounts();

  // Fetch active displays for selected machine (for modal)
  const { data: activeDisplaysForMachine = [] } = useActiveDisplaysForMachine(
    selectedDisplay?.locationType,
    selectedDisplay?.machineId
  );

  // Mutations
  const setDisplayMutation = useSetMachineDisplayMutation();
  const setDisplayBatchMutation = useSetMachineDisplayBatchMutation();
  const clearDisplayMutation = useClearDisplayByIdMutation();

  // Filter displays based on filter state
  const filteredDisplays = useMemo(() => {
    if (!allDisplays) return [];

    let result = [...allDisplays];

    // Filter by search
    if (filters.search) {
      const search = filters.search.toLowerCase();
      result = result.filter(
        (d) =>
          d.productName.toLowerCase().includes(search) ||
          d.productSku.toLowerCase().includes(search) ||
          d.machineCode.toLowerCase().includes(search)
      );
    }

    // Filter by location type
    if (filters.locationType !== "all") {
      result = result.filter((d) => d.locationType === filters.locationType);
    }

    // Filter stale only
    if (filters.staleOnly) {
      result = result.filter((d) => d.stale);
    }

    // Sort by days active (stale first)
    result.sort((a, b) => b.daysActive - a.daysActive);

    return result;
  }, [allDisplays, filters]);

  const handleFiltersChange = (next: MachineDisplayFiltersState) => {
    setFilters(next);
  };

  const handleRowClick = (display: MachineDisplay) => {
    setSelectedDisplay(display);
    setDetailModalOpen(true);
  };

  const handleSetDisplay = async (
    data: Parameters<typeof setDisplayMutation.mutateAsync>[0]
  ) => {
    try {
      await setDisplayMutation.mutateAsync(data);
      toast({
        title: "Display updated",
        description: "The product has been added to the display.",
      });
    } catch (error) {
      toast({
        title: "Error",
        description:
          error instanceof Error ? error.message : "Failed to update display.",
        variant: "destructive",
      });
      throw error;
    }
  };

  const handleSetDisplayBatch = async (data: SetMachineDisplayBatchRequest) => {
    try {
      await setDisplayBatchMutation.mutateAsync(data);
      toast({
        title: "Display updated",
        description: `${data.productIds.length} product(s) have been added to the display.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description:
          error instanceof Error ? error.message : "Failed to update display.",
        variant: "destructive",
      });
      throw error;
    }
  };

  const handleClearDisplay = async (displayId: string) => {
    try {
      await clearDisplayMutation.mutateAsync({
        displayId,
        actorId: user?.personId,
      });
      toast({
        title: "Product removed",
        description: "The product has been removed from the display.",
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to remove product from display.",
        variant: "destructive",
      });
      throw error;
    }
  };

  const staleCount = staleDisplays?.length ?? 0;

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <SidebarTrigger className="md:hidden" />
          <h1 className="text-2xl font-semibold tracking-tight">
            Machine Displays
          </h1>
          {staleCount > 0 && (
            <div className="flex items-center gap-1 text-amber-600 bg-amber-100 dark:bg-amber-950 px-2 py-1 rounded-md text-sm">
              <AlertTriangle className="h-4 w-4" />
              <span>{staleCount} stale</span>
            </div>
          )}
        </div>
        <Button onClick={() => setDialogOpen(true)}>
          <Plus className="h-4 w-4 mr-2" />
          Set Display
        </Button>
      </div>

      <MachineDisplayFilters state={filters} onChange={handleFiltersChange} />

      <Card className="py-0">
        <CardContent className="p-0">
          <MachineDisplayTable
            data={filteredDisplays}
            isLoading={isLoadingAll}
            onRowClick={handleRowClick}
          />
        </CardContent>
      </Card>

      {/* Set Display Dialog */}
      <SetDisplayDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onSubmit={handleSetDisplayBatch}
        products={products}
        locations={locations}
        actorId={user?.personId}
        isSubmitting={setDisplayBatchMutation.isPending}
      />

      {/* Detail Modal */}
      <MachineDisplayDetailModal
        display={selectedDisplay}
        open={detailModalOpen}
        onOpenChange={setDetailModalOpen}
        products={products}
        actorId={user?.personId}
        onAddProducts={handleSetDisplayBatch}
        onClearDisplay={async (displayId) => {
          await handleClearDisplay(displayId);
        }}
        isSubmitting={setDisplayBatchMutation.isPending || clearDisplayMutation.isPending}
        activeDisplaysForMachine={activeDisplaysForMachine}
      />
    </div>
  );
}
