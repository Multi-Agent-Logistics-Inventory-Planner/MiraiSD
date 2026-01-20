"use client"

import { useEffect, useMemo, useState } from "react"
import { DashboardHeader } from "@/components/dashboard-header"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ProductFilters, type ProductFiltersState } from "@/components/inventory/product-filters"
import { ProductTable } from "@/components/inventory/product-table"
import { ProductGrid } from "@/components/inventory/product-grid"
import { ProductDetailSheet } from "@/components/inventory/product-detail-sheet"
import { ProductForm } from "@/components/inventory/product-form"
import { AdjustStockDialog } from "@/components/stock/adjust-stock-dialog"
import { MovementFilters, DEFAULT_MOVEMENT_FILTERS, type MovementFiltersState } from "@/components/stock/movement-filters"
import { MovementHistoryTable } from "@/components/stock/movement-history-table"
import { TransferStockDialog } from "@/components/stock/transfer-stock-dialog"
import { useProductInventory, type ProductWithInventory } from "@/hooks/queries/use-product-inventory"
import { useMovementHistory } from "@/hooks/queries/use-movement-history"
import { useDeleteProductMutation } from "@/hooks/mutations/use-product-mutations"
import { useToast } from "@/hooks/use-toast"
import type { ProductCategory } from "@/types/api"

export default function InventoryPage() {
  const { toast } = useToast()
  const list = useProductInventory()
  const deleteMutation = useDeleteProductMutation()

  const [filters, setFilters] = useState<ProductFiltersState>({
    searchQuery: "",
    category: "all",
    status: "all",
    viewMode: "table",
  })

  const [detailOpen, setDetailOpen] = useState(false)
  const [selected, setSelected] = useState<ProductWithInventory | null>(null)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<ProductWithInventory | null>(null)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleting, setDeleting] = useState<ProductWithInventory | null>(null)

  const [adjustOpen, setAdjustOpen] = useState(false)
  const [transferOpen, setTransferOpen] = useState(false)

  const [movementFilters, setMovementFilters] = useState<MovementFiltersState>({
    ...DEFAULT_MOVEMENT_FILTERS,
  })
  const [movementPage, setMovementPage] = useState(0)
  const movementPageSize = 20

  const items = list.data ?? []

  const categories: ProductCategory[] = Array.from(
    new Set(items.map((i) => i.product.category))
  )

  const filteredItems = items.filter((row) => {
    const q = filters.searchQuery.trim().toLowerCase()
    const matchesSearch =
      q.length === 0 ||
      row.product.name.toLowerCase().includes(q) ||
      row.product.sku.toLowerCase().includes(q)
    const matchesCategory =
      filters.category === "all" || row.product.category === filters.category
    const matchesStatus = filters.status === "all" || row.status === filters.status
    return matchesSearch && matchesCategory && matchesStatus
  })

  const movementProducts = items.map((row) => ({
    id: row.product.id,
    sku: row.product.sku,
    name: row.product.name,
  }))
  const movementProductById = useMemo(() => {
    const entries = movementProducts.map((product) => [
      product.id,
      { name: product.name, sku: product.sku },
    ])
    return Object.fromEntries(entries) as Record<string, { name: string; sku: string }>
  }, [movementProducts])
  const movementProduct = items.find(
    (row) => row.product.id === movementFilters.productId
  )?.product

  useEffect(() => {
    setMovementPage(0)
  }, [
    movementFilters.productId,
    movementFilters.reason,
    movementFilters.actorId,
    movementFilters.locationId,
    movementFilters.fromDate,
    movementFilters.toDate,
  ])

  const movementQuery = useMovementHistory(
    movementFilters.productId,
    movementPage,
    movementPageSize
  )

  const movementRows = movementQuery.data?.content ?? []

  const filteredMovements = useMemo(() => {
    if (!movementFilters.productId) return []

    const actorFilter = movementFilters.actorId.trim()
    const locationFilter = movementFilters.locationId.trim()
    const reasonFilter = movementFilters.reason
    const fromDate = movementFilters.fromDate
      ? new Date(movementFilters.fromDate)
      : null
    const toDate = movementFilters.toDate
      ? new Date(movementFilters.toDate)
      : null

    if (toDate) {
      toDate.setHours(23, 59, 59, 999)
    }

    return movementRows.filter((movement) => {
      if (reasonFilter !== "all" && movement.reason !== reasonFilter) {
        return false
      }

      if (
        actorFilter &&
        (!movement.actorId || !movement.actorId.includes(actorFilter))
      ) {
        return false
      }

      if (locationFilter) {
        const matchesLocation =
          movement.fromLocationId?.includes(locationFilter) ||
          movement.toLocationId?.includes(locationFilter) ||
          movement.locationType
            .toLowerCase()
            .includes(locationFilter.toLowerCase())
        if (!matchesLocation) return false
      }

      if (fromDate || toDate) {
        const movementDate = new Date(movement.at)
        if (fromDate && movementDate < fromDate) return false
        if (toDate && movementDate > toDate) return false
      }

      return true
    })
  }, [movementFilters, movementRows])

  return (
    <div className="flex flex-col">
      <DashboardHeader title="Inventory" description="Manage your inventory items" />

      <main className="flex-1 space-y-6 p-4 md:p-6">
        <ProductFilters
          state={filters}
          categories={categories}
          onChange={setFilters}
          onAddClick={() => {
            setEditing(null)
            setFormOpen(true)
          }}
        />

        {list.error ? (
          <Card>
            <CardHeader>
              <CardTitle>Could not load products</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {list.error instanceof Error ? list.error.message : "Unknown error"}
            </CardContent>
          </Card>
        ) : null}

          {/* Inventory Content */}
          <Tabs defaultValue="inventory" className="space-y-4">
            <TabsList>
              <TabsTrigger value="inventory">Inventory</TabsTrigger>
              <TabsTrigger value="audit-log">Audit Log</TabsTrigger>
            </TabsList>
            <TabsContent value="inventory">
              {filters.viewMode === "table" ? (
                <Card>
                  <ProductTable
                    items={filteredItems}
                    isLoading={list.isLoading}
                    onSelect={(row) => {
                      setSelected(row)
                      setDetailOpen(true)
                      setMovementFilters((prev) => ({
                        ...prev,
                        productId: row.product.id,
                      }))
                    }}
                    onEdit={(row) => {
                      setEditing(row)
                      setFormOpen(true)
                    }}
                    onDelete={(row) => {
                      setDeleting(row)
                      setDeleteOpen(true)
                    }}
                  />
                </Card>
              ) : (
                <ProductGrid
                  items={filteredItems}
                  isLoading={list.isLoading}
                  onSelect={(row) => {
                    setSelected(row)
                    setDetailOpen(true)
                    setMovementFilters((prev) => ({
                      ...prev,
                      productId: row.product.id,
                    }))
                  }}
                  onEdit={(row) => {
                    setEditing(row)
                    setFormOpen(true)
                  }}
                  onDelete={(row) => {
                    setDeleting(row)
                    setDeleteOpen(true)
                  }}
                />
              )}
            </TabsContent>
            <TabsContent value="audit-log">
              <div className="space-y-4">
                <MovementFilters
                  state={movementFilters}
                  onChange={setMovementFilters}
                  products={movementProducts}
                />

                {!movementFilters.productId ? (
                  <Card>
                    <CardHeader>
                      <CardTitle>Stock Movement History</CardTitle>
                    </CardHeader>
                    <CardContent className="text-sm text-muted-foreground">
                      Select a product to view its movement history.
                    </CardContent>
                  </Card>
                ) : (
                  <Card>
                    <CardHeader>
                      <CardTitle>
                        {movementProduct
                          ? `Stock Movements for ${movementProduct.name}`
                          : "Stock Movement History"}
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <MovementHistoryTable
                        movements={filteredMovements}
                        isLoading={movementQuery.isLoading}
                        error={movementQuery.error as Error | null}
                        page={movementPage}
                        totalPages={movementQuery.data?.totalPages ?? 1}
                        onPageChange={setMovementPage}
                        productById={movementProductById}
                      />
                    </CardContent>
                  </Card>
                )}
              </div>
            </TabsContent>
          </Tabs>

        <ProductDetailSheet
          open={detailOpen}
          onOpenChange={setDetailOpen}
          item={selected}
          onAdjustClick={() => {
            if (!selected) return
            setAdjustOpen(true)
          }}
          onTransferClick={() => {
            if (!selected) return
            setTransferOpen(true)
          }}
        />

        <ProductForm
          open={formOpen}
          onOpenChange={setFormOpen}
          initialProduct={editing?.product ?? null}
        />

        <AdjustStockDialog
          open={adjustOpen}
          onOpenChange={setAdjustOpen}
          product={selected?.product ?? null}
        />
        <TransferStockDialog
          open={transferOpen}
          onOpenChange={setTransferOpen}
          product={selected?.product ?? null}
        />

        <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Delete product?</AlertDialogTitle>
              <AlertDialogDescription>
                This will permanently delete{" "}
                <span className="font-medium">
                  {deleting?.product.name ?? "this product"}
                </span>
                .
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction
                className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                onClick={async () => {
                  if (!deleting) return
                  try {
                    await deleteMutation.mutateAsync({ id: deleting.product.id })
                    toast({ title: "Product deleted" })
                  } catch (err: unknown) {
                    const msg = err instanceof Error ? err.message : "Delete failed"
                    toast({ title: "Delete failed", description: msg })
                  } finally {
                    setDeleting(null)
                  }
                }}
              >
                Delete
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </main>
    </div>
  )
}
