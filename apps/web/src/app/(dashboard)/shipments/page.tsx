"use client";

import { useState, useMemo } from "react";
import {
  Plus,
  Search,
  Filter,
  Truck,
  Package,
  AlertTriangle,
  CheckCircle,
} from "lucide-react";
import { DashboardHeader } from "@/components/dashboard-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import { Can, Permission } from "@/components/rbac";
import {
  ShipmentsTable,
  ShipmentDetailSheet,
  ShipmentCreateDialog,
  ShipmentReceiveDialog,
} from "@/components/shipments";
import { useShipments } from "@/hooks/queries/use-shipments";
import {
  useUpdateShipmentMutation,
  useDeleteShipmentMutation,
} from "@/hooks/mutations/use-shipment-mutations";
import { useToast } from "@/hooks/use-toast";
import { ShipmentStatus, type Shipment } from "@/types/api";

export default function ShipmentsPage() {
  const { toast } = useToast();
  const shipmentsQuery = useShipments();
  const updateMutation = useUpdateShipmentMutation();
  const deleteMutation = useDeleteShipmentMutation();

  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");

  // Dialog/sheet states
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [detailSheetOpen, setDetailSheetOpen] = useState(false);
  const [receiveDialogOpen, setReceiveDialogOpen] = useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedShipment, setSelectedShipment] = useState<Shipment | null>(
    null
  );

  const shipments = shipmentsQuery.data ?? [];

  // Filter shipments
  const filteredShipments = useMemo(() => {
    return shipments.filter((shipment) => {
      const q = searchQuery.trim().toLowerCase();
      const matchesSearch =
        q.length === 0 ||
        shipment.shipmentNumber.toLowerCase().includes(q) ||
        shipment.supplierName?.toLowerCase().includes(q);
      const matchesStatus =
        statusFilter === "all" || shipment.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [shipments, searchQuery, statusFilter]);

  // Stats
  const totalShipments = shipments.length;
  const pending = shipments.filter(
    (s) => s.status === ShipmentStatus.PENDING
  ).length;
  const inTransit = shipments.filter(
    (s) => s.status === ShipmentStatus.IN_TRANSIT
  ).length;
  const delivered = shipments.filter(
    (s) => s.status === ShipmentStatus.DELIVERED
  ).length;

  function handleRowClick(shipment: Shipment) {
    setSelectedShipment(shipment);
    setDetailSheetOpen(true);
  }

  function handleReceiveClick() {
    setDetailSheetOpen(false);
    setReceiveDialogOpen(true);
  }

  function handleEditClick() {
    toast({
      title: "Edit not implemented",
      description: "Edit shipment functionality will be added later.",
    });
  }

  function handleCancelClick() {
    setDetailSheetOpen(false);
    setCancelDialogOpen(true);
  }

  async function handleConfirmCancel() {
    if (!selectedShipment) return;

    try {
      await updateMutation.mutateAsync({
        id: selectedShipment.id,
        payload: {
          ...buildShipmentRequest(selectedShipment),
          status: ShipmentStatus.CANCELLED,
        },
      });
      toast({ title: "Shipment cancelled" });
      setCancelDialogOpen(false);
      setSelectedShipment(null);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to cancel shipment";
      toast({ title: "Error", description: message });
    }
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

  // Helper to build ShipmentRequest from existing Shipment
  function buildShipmentRequest(shipment: Shipment) {
    return {
      shipmentNumber: shipment.shipmentNumber,
      supplierName: shipment.supplierName,
      status: shipment.status,
      orderDate: shipment.orderDate,
      expectedDeliveryDate: shipment.expectedDeliveryDate,
      totalCost: shipment.totalCost,
      notes: shipment.notes,
      createdBy: shipment.createdBy?.id ?? "",
      items: shipment.items.map((item) => ({
        itemId: item.item.id,
        orderedQuantity: item.orderedQuantity,
        unitCost: item.unitCost,
        destinationLocationType: item.destinationLocationType,
        destinationLocationId: item.destinationLocationId,
        notes: item.notes,
      })),
    };
  }

  return (
    <div className="flex flex-col">
      <DashboardHeader
        title="Shipments"
      />
      <main className="flex-1 space-y-6 p-4 md:p-6">
        {/* Stats Cards */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">
                Total Shipments
              </CardTitle>
              <Package className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{totalShipments}</div>
              <p className="text-xs text-muted-foreground">All time</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Pending</CardTitle>
              <AlertTriangle className="h-4 w-4 text-amber-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-amber-600">{pending}</div>
              <p className="text-xs text-muted-foreground">Awaiting shipment</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">In Transit</CardTitle>
              <Truck className="h-4 w-4 text-blue-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-blue-600">{inTransit}</div>
              <p className="text-xs text-muted-foreground">Currently shipping</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Delivered</CardTitle>
              <CheckCircle className="h-4 w-4 text-green-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">
                {delivered}
              </div>
              <p className="text-xs text-muted-foreground">Completed</p>
            </CardContent>
          </Card>
        </div>

        {/* Filters and Actions */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-1 items-center gap-2">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search shipments..."
                className="pl-8"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[140px]">
                <Filter className="mr-2 h-4 w-4" />
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Status</SelectItem>
                <SelectItem value={ShipmentStatus.PENDING}>Pending</SelectItem>
                <SelectItem value={ShipmentStatus.IN_TRANSIT}>
                  In Transit
                </SelectItem>
                <SelectItem value={ShipmentStatus.DELIVERED}>
                  Delivered
                </SelectItem>
                <SelectItem value={ShipmentStatus.CANCELLED}>
                  Cancelled
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center gap-2">
            <Can permission={Permission.SHIPMENTS_CREATE}>
              <Button onClick={() => setCreateDialogOpen(true)}>
                <Plus className="mr-2 h-4 w-4" />
                New Shipment
              </Button>
            </Can>
          </div>
        </div>

        {/* Shipments Table */}
        <Card>
          <CardContent className="p-0">
            <ShipmentsTable
              shipments={filteredShipments}
              isLoading={shipmentsQuery.isLoading}
              onRowClick={handleRowClick}
            />
          </CardContent>
        </Card>

        {/* Error State */}
        {shipmentsQuery.error && (
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
        )}
      </main>

      {/* Dialogs and Sheets */}
      <ShipmentCreateDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
      />

      <ShipmentDetailSheet
        open={detailSheetOpen}
        onOpenChange={setDetailSheetOpen}
        shipment={selectedShipment}
        onEditClick={handleEditClick}
        onCancelClick={handleCancelClick}
        onReceiveClick={handleReceiveClick}
        onDeleteClick={handleDeleteClick}
      />

      <ShipmentReceiveDialog
        open={receiveDialogOpen}
        onOpenChange={setReceiveDialogOpen}
        shipment={selectedShipment}
      />

      {/* Cancel Confirmation Dialog */}
      <AlertDialog open={cancelDialogOpen} onOpenChange={setCancelDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Cancel shipment?</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to cancel shipment{" "}
              <span className="font-medium font-mono">
                {selectedShipment?.shipmentNumber}
              </span>
              ? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep Shipment</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90 hover:text-white active:text-white"
              onClick={handleConfirmCancel}
              disabled={updateMutation.isPending}
            >
              Cancel Shipment
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

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
