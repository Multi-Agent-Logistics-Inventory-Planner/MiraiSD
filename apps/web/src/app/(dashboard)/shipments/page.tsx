"use client";

import { useState } from "react";
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
  ShipmentsList,
  ShipmentDetailSheet,
  ShipmentCreateDialog,
  ShipmentReceiveDialog,
  ShipmentHeader,
  ShipmentFilters,
  ShipmentPagination,
} from "@/components/shipments";
import { useShipments, useShipmentDisplayStatusCounts } from "@/hooks/queries/use-shipments";
import { useDeleteShipmentMutation, useUpdateShipmentMutation, useUndoReceiveShipmentMutation } from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import type { Shipment } from "@/types/api";
import type { ShipmentDisplayStatus } from "@/lib/shipment-utils";

const PAGE_SIZE = 5;

export default function ShipmentsPage() {
  const { toast } = useToast();
  const deleteMutation = useDeleteShipmentMutation();
  const updateMutation = useUpdateShipmentMutation();
  const undoReceiveMutation = useUndoReceiveShipmentMutation();

  const [searchQuery, setSearchQuery] = useState("");
  const [activeTab, setActiveTab] = useState<ShipmentDisplayStatus>("ACTIVE");
  const [page, setPage] = useState(0);

  // Dialog/sheet states
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [detailSheetOpen, setDetailSheetOpen] = useState(false);
  const [receiveDialogOpen, setReceiveDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [undoDialogOpen, setUndoDialogOpen] = useState(false);
  const [selectedShipment, setSelectedShipment] = useState<Shipment | null>(
    null
  );

  // Use server-side pagination with display status and search filter
  const shipmentsQuery = useShipments(
    { displayStatus: activeTab, search: searchQuery || undefined },
    page,
    PAGE_SIZE
  );

  // Get counts for each status tab (from server)
  const statusCountsQuery = useShipmentDisplayStatusCounts();

  const shipments = shipmentsQuery.data?.content ?? [];
  const totalElements = shipmentsQuery.data?.totalElements ?? 0;

  const statusCounts = statusCountsQuery.data ?? { ACTIVE: 0, PARTIAL: 0, COMPLETED: 0 };

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

  function handleEditClick() {
    setDetailSheetOpen(false);
    setCreateDialogOpen(true);
  }

  function handleUndoReceiveClick() {
    setDetailSheetOpen(false);
    setUndoDialogOpen(true);
  }

  async function handleConfirmUndoReceive() {
    if (!selectedShipment) return;

    try {
      await undoReceiveMutation.mutateAsync({ id: selectedShipment.id });
      toast({
        title: "Shipment receipt reversed",
        description: "Inventory has been restored to pre-receipt state.",
      });
      setUndoDialogOpen(false);
      setSelectedShipment(null);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to undo shipment receipt";
      toast({ title: "Error", description: message, variant: "destructive" });
    }
  }

  async function handleTrackingUpdate(trackingId: string) {
    if (!selectedShipment) return;
    try {
      const updated = await updateMutation.mutateAsync({
        id: selectedShipment.id,
        payload: {
          shipmentNumber: selectedShipment.shipmentNumber,
          status: selectedShipment.status,
          orderDate: selectedShipment.orderDate,
          trackingId,
          items: selectedShipment.items.map((item) => ({
            itemId: item.item.id,
            orderedQuantity: item.orderedQuantity,
            unitCost: item.unitCost,
          })),
        },
      });
      setSelectedShipment(updated);
      toast({ title: "Tracking number updated" });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to update tracking number";
      toast({ title: "Error", description: message });
      throw err;
    }
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
            onAddClick={() => {
              setSelectedShipment(null);
              setCreateDialogOpen(true);
            }}
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
        <ShipmentsList
          shipments={shipments}
          isLoading={shipmentsQuery.isLoading}
          onShipmentClick={handleRowClick}
        />
      )}

      <ShipmentPagination
        page={page}
        pageSize={PAGE_SIZE}
        totalItems={totalElements}
        isLoading={shipmentsQuery.isLoading}
        onPageChange={setPage}
      />

      {/* Dialogs and Sheets */}
      <ShipmentCreateDialog
        open={createDialogOpen}
        onOpenChange={(open) => {
          setCreateDialogOpen(open);
          if (!open) setSelectedShipment(null);
        }}
        initialShipment={selectedShipment}
      />

      <ShipmentDetailSheet
        open={detailSheetOpen}
        onOpenChange={setDetailSheetOpen}
        shipment={selectedShipment}
        onReceiveClick={handleReceiveClick}
        onDeleteClick={handleDeleteClick}
        onEditClick={handleEditClick}
        onUndoReceiveClick={handleUndoReceiveClick}
        onTrackingUpdate={handleTrackingUpdate}
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

      {/* Undo Receipt Confirmation Dialog */}
      <AlertDialog open={undoDialogOpen} onOpenChange={setUndoDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Undo shipment receipt?</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-2">
                <p>
                  This will reverse all inventory additions from shipment{" "}
                  <span className="font-medium font-mono">
                    {selectedShipment?.shipmentNumber}
                  </span>
                  . The shipment will return to PENDING status and can be received again or deleted.
                </p>
                <p className="text-amber-600 font-medium">
                  Warning: If items from this shipment have been sold or transferred, the undo will fail.
                </p>
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-amber-600 text-white hover:bg-amber-700"
              onClick={handleConfirmUndoReceive}
              disabled={undoReceiveMutation.isPending}
            >
              {undoReceiveMutation.isPending ? "Reversing..." : "Undo Receipt"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
