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
  DEFAULT_PRODUCT_SORT,
  buildParentNameMap,
  buildKujiCategoryIds,
  compareProducts,
} from "@/components/products";
import type { ProductSort } from "@/components/products";
import { AdjustStockDialog } from "@/components/stock/adjust-stock-dialog";
import { TransferStockDialog } from "@/components/stock/transfer-stock-dialog";
import {
  useProductInventory,
  type ProductWithInventory,
} from "@/hooks/queries/use-product-inventory";
import { useCategories } from "@/hooks/queries/use-categories";

const PAGE_SIZE = 20;

export default function ProductsPage() {
  // Show only root products (no parent) by default
  const list = useProductInventory(true);
  const { data: categories } = useCategories();

  const [filters, setFilters] = useState<ProductFiltersState>(
    DEFAULT_PRODUCT_FILTERS,
  );
  const [sort, setSort] = useState<ProductSort>(DEFAULT_PRODUCT_SORT);
  const [page, setPage] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [selected, setSelected] = useState<ProductWithInventory | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ProductWithInventory | null>(null);

  const [adjustOpen, setAdjustOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);

  const items = list.data ?? [];

  const filteredItems = useMemo(() => {
    return items.filter((row) => {
      const q = filters.search.trim().toLowerCase();
      const matchesSearch =
        q.length === 0 ||
        row.product.name.toLowerCase().includes(q) ||
        (row.product.sku?.toLowerCase().includes(q) ?? false);

      let matchesCategory = true;
      if (filters.selectedSubcategoryId) {
        matchesCategory = row.product.category.id === filters.selectedSubcategoryId;
      } else if (filters.selectedCategoryId) {
        const selectedCat = (categories ?? []).find(
          (c) => c.id === filters.selectedCategoryId
        );
        const childIds = selectedCat?.children.map((c) => c.id) ?? [];
        matchesCategory =
          row.product.category.id === filters.selectedCategoryId ||
          childIds.includes(row.product.category.id);
      }

      return matchesSearch && matchesCategory;
    });
  }, [items, filters, categories]);

  const parentNameMap = useMemo(
    () => buildParentNameMap(categories ?? []),
    [categories],
  );

  const kujiCategoryIds = useMemo(
    () => buildKujiCategoryIds(categories ?? []),
    [categories],
  );

  const sortedItems = useMemo(() => {
    return [...filteredItems].sort((a, b) =>
      compareProducts(a, b, sort, parentNameMap),
    );
  }, [filteredItems, sort, parentNameMap]);

  const paginatedItems = useMemo(() => {
    const start = page * PAGE_SIZE;
    return sortedItems.slice(start, start + PAGE_SIZE);
  }, [sortedItems, page]);

  const handleFiltersChange = (next: ProductFiltersState) => {
    setFilters(next);
    setPage(0);
  };

  const handleSortChange = (next: ProductSort) => {
    setSort(next);
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
        categories={categories ?? []}
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
        <Card className="p-2 border-none">
          <CardContent className="p-0">
            <ProductTable
              items={paginatedItems}
              isLoading={list.isLoading}
              onSelect={handleSelect}
              parentNameMap={parentNameMap}
              kujiCategoryIds={kujiCategoryIds}
              sort={sort}
              onSortChange={handleSortChange}
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
      />

      <TransferStockDialog
        open={transferOpen}
        onOpenChange={setTransferOpen}
      />
    </div>
  );
}
