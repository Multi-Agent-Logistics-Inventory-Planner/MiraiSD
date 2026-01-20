"use client"

import { useState } from "react"
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ProductFilters, type ProductFiltersState } from "@/components/inventory/product-filters"
import { ProductTable } from "@/components/inventory/product-table"
import { ProductGrid } from "@/components/inventory/product-grid"
import { ProductDetailSheet } from "@/components/inventory/product-detail-sheet"
import { ProductForm } from "@/components/inventory/product-form"
import { useProductInventory, type ProductWithInventory } from "@/hooks/queries/use-product-inventory"
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
              <Card>
                <CardHeader>
                  <CardTitle>Audit Log</CardTitle>
                </CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Timestamp</TableHead>
                        <TableHead>User</TableHead>
                        <TableHead>Action</TableHead>
                        <TableHead>Item</TableHead>
                        <TableHead>Details</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      <TableRow>
                        <TableCell>2026-01-19 10:30</TableCell>
                        <TableCell>John Smith</TableCell>
                        <TableCell>Stock Adjustment</TableCell>
                        <TableCell>Wireless Headphones</TableCell>
                        <TableCell>20 units → 15 units (-5)</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell>2026-01-19 09:15</TableCell>
                        <TableCell>Sarah Johnson</TableCell>
                        <TableCell>Received Shipment</TableCell>
                        <TableCell>Bluetooth Speaker</TableCell>
                        <TableCell>+45 units</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell>2026-01-18 16:45</TableCell>
                        <TableCell>Mike Wilson</TableCell>
                        <TableCell>Sale</TableCell>
                        <TableCell>USB-C Cable</TableCell>
                        <TableCell>5 units → 0 units (-5)</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell>2026-01-18 14:20</TableCell>
                        <TableCell>John Smith</TableCell>
                        <TableCell>Price Update</TableCell>
                        <TableCell>Laptop Stand</TableCell>
                        <TableCell>$29.99 → $34.99</TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell>2026-01-17 11:00</TableCell>
                        <TableCell>Sarah Johnson</TableCell>
                        <TableCell>New Item Added</TableCell>
                        <TableCell>Monitor Arm</TableCell>
                        <TableCell>Initial stock: 30 units</TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>

        <ProductDetailSheet
          open={detailOpen}
          onOpenChange={setDetailOpen}
          item={selected}
        />

        <ProductForm
          open={formOpen}
          onOpenChange={setFormOpen}
          initialProduct={editing?.product ?? null}
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
