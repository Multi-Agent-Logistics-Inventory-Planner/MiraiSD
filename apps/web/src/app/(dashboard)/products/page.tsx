"use client";

import { useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  ProductHeader,
  ProductFilters,
  ProductFiltersState,
  ProductTable,
  ProductPagination,
  ProductModal,
  ProductForm,
  DEFAULT_PRODUCT_FILTERS,
} from "@/components/products";
import { AdjustStockDialog } from "@/components/stock/adjust-stock-dialog";
import { TransferStockDialog } from "@/components/stock/transfer-stock-dialog";
import {
  useProductInventory,
  type ProductWithInventory,
} from "@/hooks/queries/use-product-inventory";
import type { ProductCategory } from "@/types/api";

const PAGE_SIZE = 20;

export default function ProductsPage() {
  const list = useProductInventory();

  const [filters, setFilters] = useState<ProductFiltersState>(
    DEFAULT_PRODUCT_FILTERS,
  );
  const [page, setPage] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [selected, setSelected] = useState<ProductWithInventory | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ProductWithInventory | null>(null);

  const [adjustOpen, setAdjustOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);

  const items = list.data ?? [];

  const categories: ProductCategory[] = Array.from(
    new Set(items.map((i) => i.product.category)),
  );

  const filteredItems = useMemo(() => {
    return items.filter((row) => {
      const q = filters.search.trim().toLowerCase();
      const matchesSearch =
        q.length === 0 ||
        row.product.name.toLowerCase().includes(q) ||
        row.product.sku.toLowerCase().includes(q);
      const matchesCategory =
        filters.category === "all" || row.product.category === filters.category;
      const matchesSubcategory =
        filters.subcategory === "all" || row.product.subcategory === filters.subcategory;
      const matchesStatus =
        filters.status === "all" || row.status === filters.status;
      return matchesSearch && matchesCategory && matchesSubcategory && matchesStatus;
    });
  }, [items, filters]);

  const paginatedItems = useMemo(() => {
    const start = page * PAGE_SIZE;
    return filteredItems.slice(start, start + PAGE_SIZE);
  }, [filteredItems, page]);

  const handleFiltersChange = (next: ProductFiltersState) => {
    setFilters(next);
    setPage(0);
  };

  const handleSelect = (row: ProductWithInventory) => {
    setSelected(row);
    setDetailOpen(true);
  };

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <ProductHeader />

      <ProductFilters
        state={filters}
        onChange={handleFiltersChange}
        categories={categories}
        onAddClick={() => {
          setEditing(null);
          setFormOpen(true);
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
      ) : (
        <Card className="py-0">
          <CardContent className="p-0">
            <ProductTable
              items={paginatedItems}
              isLoading={list.isLoading}
              onSelect={handleSelect}
            />
          </CardContent>
        </Card>
      )}

      <ProductPagination
        page={page}
        pageSize={PAGE_SIZE}
        totalItems={filteredItems.length}
        isLoading={list.isLoading}
        onPageChange={setPage}
      />

      <ProductModal
        open={detailOpen}
        onOpenChange={setDetailOpen}
        product={selected}
        onAdjustClick={() => setAdjustOpen(true)}
        onTransferClick={() => setTransferOpen(true)}
        onEditClick={() => {
          if (selected) {
            setEditing(selected);
            setFormOpen(true);
          }
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
    </div>
  );
}
