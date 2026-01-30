"use client";

import { useState, useMemo } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
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
  ShipmentsTable,
  ShipmentDetailSheet,
  ShipmentCreateDialog,
  ShipmentReceiveDialog,
  ShipmentHeader,
  ShipmentFilters,
  ShipmentPagination,
} from "@/components/shipments";
import { useShipments } from "@/hooks/queries/use-shipments";
import { useDeleteShipmentMutation } from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import type { Shipment } from "@/types/api";
import {
  filterShipmentsByDisplayStatus,
  getShipmentDisplayStatusCounts,
  type ShipmentDisplayStatus,
} from "@/lib/shipment-utils";

const PAGE_SIZE = 20;

export default function ShipmentsPage() {
  const { toast } = useToast();
  const shipmentsQuery = useShipments();
  const deleteMutation = useDeleteShipmentMutation();

  const [searchQuery, setSearchQuery] = useState("");
  const [activeTab, setActiveTab] = useState<ShipmentDisplayStatus>("ACTIVE");
  const [page, setPage] = useState(0);

  // Dialog/sheet states
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [detailSheetOpen, setDetailSheetOpen] = useState(false);
  const [receiveDialogOpen, setReceiveDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedShipment, setSelectedShipment] = useState<Shipment | null>(
    null
  );

  const shipments = shipmentsQuery.data ?? [];

  // Get counts for each status tab
  const statusCounts = useMemo(() => {
    return getShipmentDisplayStatusCounts(shipments);
  }, [shipments]);

  // Filter shipments by tab and search
  const filteredShipments = useMemo(() => {
    const byStatus = filterShipmentsByDisplayStatus(shipments, activeTab);

    if (searchQuery.trim().length === 0) {
      return byStatus;
    }

    const q = searchQuery.trim().toLowerCase();
    return byStatus.filter(
      (shipment) =>
        shipment.shipmentNumber.toLowerCase().includes(q) ||
        shipment.supplierName?.toLowerCase().includes(q)
    );
  }, [shipments, activeTab, searchQuery]);

  // Paginate filtered shipments
  const paginatedShipments = useMemo(() => {
    const start = page * PAGE_SIZE;
    return filteredShipments.slice(start, start + PAGE_SIZE);
  }, [filteredShipments, page]);

  // Reset page when tab or search changes
  const handleTabChange = (value: string) => {
    setActiveTab(value as ShipmentDisplayStatus);
    setPage(0);
  };

  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
    setPage(0);
  };

  function handleRowClick(shipment: Shipment) {
    setSelectedShipment(shipment);
    setDetailSheetOpen(true);
  }

  function handleReceiveClick() {
    setDetailSheetOpen(false);
    setReceiveDialogOpen(true);
  }

  function handleDeleteClick() {
    setDetailSheetOpen(false);
    setDeleteDialogOpen(true);
  }

  async function handleConfirmDelete() {
    if (!selectedShipment) return;

    try {
      await deleteMutation.mutateAsync({ id: selectedShipment.id });
      toast({ title: "Shipment deleted" });
      setDeleteDialogOpen(false);
      setSelectedShipment(null);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to delete shipment";
      toast({ title: "Error", description: message });
    }
  }

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <ShipmentHeader />

      <Tabs value={activeTab} onValueChange={handleTabChange}>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <TabsList>
            <TabsTrigger value="ACTIVE">
              Active ({statusCounts.ACTIVE})
            </TabsTrigger>
            <TabsTrigger value="PARTIAL">
              Partial ({statusCounts.PARTIAL})
            </TabsTrigger>
            <TabsTrigger value="COMPLETED">
              Completed ({statusCounts.COMPLETED})
            </TabsTrigger>
          </TabsList>

          <ShipmentFilters
            search={searchQuery}
            onSearchChange={handleSearchChange}
            onAddClick={() => setCreateDialogOpen(true)}
          />
        </div>
      </Tabs>

      {shipmentsQuery.error ? (
        <Card>
          <CardHeader>
            <CardTitle>Could not load shipments</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            {shipmentsQuery.error instanceof Error
              ? shipmentsQuery.error.message
              : "Unknown error"}
          </CardContent>
        </Card>
      ) : (
        <Card className="py-0">
          <CardContent className="p-0">
            <ShipmentsTable
              shipments={paginatedShipments}
              isLoading={shipmentsQuery.isLoading}
              onRowClick={handleRowClick}
              showActualDeliveryDate={activeTab === "COMPLETED"}
            />
          </CardContent>
        </Card>
      )}

      <ShipmentPagination
        page={page}
        pageSize={PAGE_SIZE}
        totalItems={filteredShipments.length}
        isLoading={shipmentsQuery.isLoading}
        onPageChange={setPage}
      />

      {/* Dialogs and Sheets */}
      <ShipmentCreateDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
      />

      <ShipmentDetailSheet
        open={detailSheetOpen}
        onOpenChange={setDetailSheetOpen}
        shipment={selectedShipment}
        onReceiveClick={handleReceiveClick}
        onDeleteClick={handleDeleteClick}
      />

      <ShipmentReceiveDialog
        open={receiveDialogOpen}
        onOpenChange={setReceiveDialogOpen}
        shipment={selectedShipment}
      />

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete shipment?</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete shipment{" "}
              <span className="font-medium font-mono">
                {selectedShipment?.shipmentNumber}
              </span>
              ? This action cannot be undone. Delivered shipments cannot be
              deleted.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep Shipment</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90 hover:text-white active:text-white"
              onClick={handleConfirmDelete}
              disabled={deleteMutation.isPending}
            >
              Delete Shipment
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
