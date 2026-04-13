"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  ProductHeader,
  ProductFilters,
  ProductFiltersState,
  ProductTable,
  ProductPagination,
  DEFAULT_PRODUCT_FILTERS,
  DEFAULT_PRODUCT_SORT,
  buildParentNameMap,
  buildKujiCategoryIds,
  compareProducts,
} from "@/components/products";
import type { ProductSort } from "@/components/products";
import type { PreselectedProductInfo } from "@/components/stock/adjust-stock-dialog";

const ProductModal = dynamic(
  () =>
    import("@/components/products/product-modal").then((m) => ({
      default: m.ProductModal,
    })),
  { ssr: false }
);

const ProductForm = dynamic(
  () =>
    import("@/components/products/product-form").then((m) => ({
      default: m.ProductForm,
    })),
  { ssr: false }
);

const AdjustStockDialog = dynamic(
  () =>
    import("@/components/stock/adjust-stock-dialog").then((m) => ({
      default: m.AdjustStockDialog,
    })),
  { ssr: false }
);

const TransferStockDialog = dynamic(
  () =>
    import("@/components/stock/transfer-stock-dialog").then((m) => ({
      default: m.TransferStockDialog,
    })),
  { ssr: false }
);
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
  const [adjustPreselectedProduct, setAdjustPreselectedProduct] =
    useState<PreselectedProductInfo | null>(null);
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferPreselectedProduct, setTransferPreselectedProduct] =
    useState<PreselectedProductInfo | null>(null);

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
        matchesCategory =
          row.product.category.id === filters.selectedSubcategoryId;
      } else if (filters.selectedCategoryId) {
        const selectedCat = (categories ?? []).find(
          (c) => c.id === filters.selectedCategoryId,
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
        <Card className="p-2 dark:border-none">
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
        onAdjustClick={(preselectedProduct) => {
          setAdjustPreselectedProduct(preselectedProduct);
          setAdjustOpen(true);
        }}
        onTransferClick={(preselectedProduct) => {
          setTransferPreselectedProduct(preselectedProduct);
          setTransferOpen(true);
        }}
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
        onOpenChange={(open) => {
          setAdjustOpen(open);
          if (!open) {
            setAdjustPreselectedProduct(null);
          }
        }}
        preselectedProduct={adjustPreselectedProduct}
      />

      <TransferStockDialog
        open={transferOpen}
        onOpenChange={(open) => {
          setTransferOpen(open);
          if (!open) {
            setTransferPreselectedProduct(null);
          }
        }}
        preselectedProduct={transferPreselectedProduct}
      />
    </div>
  );
}
