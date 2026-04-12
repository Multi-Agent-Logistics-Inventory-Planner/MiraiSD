"use client";

import { useState, useMemo } from "react";
import { useSuppliers } from "@/hooks/queries/use-suppliers";
import { ProductPagination } from "@/components/products/product-pagination";
import { useDeleteSupplierMutation } from "@/hooks/mutations/use-supplier-mutations";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipTrigger,
  TooltipContent,
} from "@/components/ui/tooltip";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
import { MoreHorizontal, Plus, Search, Pencil, UserX, UserCheck, Package, Eye, HelpCircle } from "lucide-react";
import { SupplierModal } from "./supplier-modal";
import { BulkAssignModal } from "./bulk-assign-modal";
import { ViewProductsModal } from "./view-products-modal";
import type { Supplier } from "@/types/api";

const PAGE_SIZE = 10;

export function SuppliersTab() {
  const { toast } = useToast();
  const [search, setSearch] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [selectedSupplier, setSelectedSupplier] = useState<Supplier | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [bulkAssignOpen, setBulkAssignOpen] = useState(false);
  const [viewProductsOpen, setViewProductsOpen] = useState(false);
  const [deactivateDialogOpen, setDeactivateDialogOpen] = useState(false);
  const [page, setPage] = useState(0);

  const { data: suppliers, isLoading } = useSuppliers({
    q: search || undefined,
    active: showInactive ? undefined : true,
  });

  const paginatedSuppliers = useMemo(() => {
    if (!suppliers) return [];
    const start = page * PAGE_SIZE;
    return suppliers.slice(start, start + PAGE_SIZE);
  }, [suppliers, page]);

  const deleteMutation = useDeleteSupplierMutation();

  const handleEdit = (supplier: Supplier) => {
    setSelectedSupplier(supplier);
    setModalOpen(true);
  };

  const handleCreate = () => {
    setSelectedSupplier(null);
    setModalOpen(true);
  };

  const handleBulkAssign = (supplier: Supplier) => {
    setSelectedSupplier(supplier);
    setBulkAssignOpen(true);
  };

  const handleViewProducts = (supplier: Supplier) => {
    setSelectedSupplier(supplier);
    setViewProductsOpen(true);
  };

  const handleDeactivate = (supplier: Supplier) => {
    setSelectedSupplier(supplier);
    setDeactivateDialogOpen(true);
  };

  const confirmDeactivate = async () => {
    if (!selectedSupplier) return;
    try {
      await deleteMutation.mutateAsync({ id: selectedSupplier.id });
      toast({ title: `Supplier ${selectedSupplier.isActive ? "deactivated" : "reactivated"}` });
      setDeactivateDialogOpen(false);
      setSelectedSupplier(null);
    } catch {
      toast({ title: "Error", description: "Failed to update supplier status" });
    }
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search suppliers..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              className="pl-9 w-64"
            />
          </div>
          <Button
            variant={showInactive ? "default" : "outline"}
            className="shrink-0 border dark:bg-input dark:border-[#41413d] dark:text-[#a1a1a1]"
            onClick={() => {
              setShowInactive(!showInactive);
              setPage(0);
            }}
          >
            {showInactive ? "Hide Inactive" : "Show Inactive"}
          </Button>
        </div>
        <Button onClick={handleCreate} className="shrink-0 text-white bg-brand-primary hover:bg-brand-primary-hover">
          <Plus className="h-4 w-4 mr-2" />
          Add Supplier
        </Button>
      </div>

      {/* Table */}
      <Card className="p-2 dark:border-none">
        <CardContent className="p-0 overflow-hidden sm:overflow-visible w-full">
        <Table className="border-none table-fixed w-full">
          <DataTableHeader>
            <TableHead className="text-left rounded-l-lg">Supplier Name</TableHead>
            <TableHead className="text-right w-24">Shipments</TableHead>
            <TableHead className="text-right w-24">Products</TableHead>
            <TableHead className="text-right w-32">
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="inline-flex items-center gap-1 cursor-help">
                    Avg Lead Time
                    <HelpCircle className="h-3 w-3 text-muted-foreground" />
                  </span>
                </TooltipTrigger>
                <TooltipContent side="top" className="max-w-64">
                  Average number of days between order date and delivery date, calculated from delivered shipments
                </TooltipContent>
              </Tooltip>
            </TableHead>
            <TableHead className="text-right w-24">
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="inline-flex items-center gap-1 cursor-help">
                    Sigma L
                    <HelpCircle className="h-3 w-3 text-muted-foreground" />
                  </span>
                </TooltipTrigger>
                <TooltipContent side="top" className="max-w-64">
                  Standard deviation of lead time - measures delivery reliability. Lower values indicate more consistent delivery times. Used to calculate safety stock levels.
                </TooltipContent>
              </Tooltip>
            </TableHead>
            <TableHead className="w-20">Status</TableHead>
            <TableHead className="w-14 rounded-r-lg"></TableHead>
          </DataTableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell className="py-2 rounded-l-lg"><Skeleton className="h-4 w-32" /></TableCell>
                  <TableCell className="text-right"><Skeleton className="h-4 w-12 ml-auto" /></TableCell>
                  <TableCell className="text-right"><Skeleton className="h-4 w-12 ml-auto" /></TableCell>
                  <TableCell className="text-right"><Skeleton className="h-4 w-16 ml-auto" /></TableCell>
                  <TableCell className="text-right"><Skeleton className="h-4 w-12 ml-auto" /></TableCell>
                  <TableCell><Skeleton className="h-5 w-16" /></TableCell>
                  <TableCell className="rounded-r-lg"><Skeleton className="h-8 w-8" /></TableCell>
                </TableRow>
              ))
            ) : paginatedSuppliers.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center text-muted-foreground">
                  No suppliers found
                </TableCell>
              </TableRow>
            ) : (
              paginatedSuppliers.map((supplier) => (
                <TableRow key={supplier.id}>
                  <TableCell className="py-2 rounded-l-lg font-medium">{supplier.displayName}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {supplier.shipmentCount ?? 0}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {supplier.productCount ?? 0}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {supplier.avgLeadTimeDays != null
                      ? `${supplier.avgLeadTimeDays.toFixed(1)} days`
                      : "-"}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {supplier.sigmaL != null
                      ? supplier.sigmaL.toFixed(1)
                      : "-"}
                  </TableCell>
                  <TableCell>
                    <Badge variant={supplier.isActive ? "default" : "secondary"}>
                      {supplier.isActive ? "Active" : "Inactive"}
                    </Badge>
                  </TableCell>
                  <TableCell className="rounded-r-lg">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => handleEdit(supplier)}>
                          <Pencil className="h-4 w-4 mr-2" />
                          Edit
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleViewProducts(supplier)}>
                          <Eye className="h-4 w-4 mr-2" />
                          View Products
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleBulkAssign(supplier)}>
                          <Package className="h-4 w-4 mr-2" />
                          Assign Products
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleDeactivate(supplier)}>
                          {supplier.isActive ? (
                            <>
                              <UserX className="h-4 w-4 mr-2" />
                              Deactivate
                            </>
                          ) : (
                            <>
                              <UserCheck className="h-4 w-4 mr-2" />
                              Reactivate
                            </>
                          )}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        </CardContent>
      </Card>

      <ProductPagination
        page={page}
        pageSize={PAGE_SIZE}
        totalItems={suppliers?.length ?? 0}
        isLoading={isLoading}
        onPageChange={setPage}
      />

      {/* Modals */}
      <SupplierModal
        open={modalOpen}
        onOpenChange={setModalOpen}
        supplier={selectedSupplier}
      />

      <BulkAssignModal
        open={bulkAssignOpen}
        onOpenChange={setBulkAssignOpen}
        supplier={selectedSupplier}
      />

      <ViewProductsModal
        open={viewProductsOpen}
        onOpenChange={setViewProductsOpen}
        supplier={selectedSupplier}
      />

      {/* Deactivate Confirmation */}
      <AlertDialog open={deactivateDialogOpen} onOpenChange={setDeactivateDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {selectedSupplier?.isActive ? "Deactivate" : "Reactivate"} supplier?
            </AlertDialogTitle>
            <AlertDialogDescription>
              {selectedSupplier?.isActive
                ? `This will hide "${selectedSupplier?.displayName}" from dropdowns and autocomplete. Existing shipments will retain their supplier reference.`
                : `This will make "${selectedSupplier?.displayName}" available again in dropdowns and autocomplete.`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDeactivate} disabled={deleteMutation.isPending}>
              {selectedSupplier?.isActive ? "Deactivate" : "Reactivate"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
