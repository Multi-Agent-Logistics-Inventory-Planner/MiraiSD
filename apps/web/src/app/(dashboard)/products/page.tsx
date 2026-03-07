"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
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
import { useCategories } from "@/hooks/queries/use-categories";

const PAGE_SIZE = 20;

export default function ProductsPage() {
  const router = useRouter();
  // Show only root products (no parent) by default
  const list = useProductInventory(true);
  const { data: categories } = useCategories();

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

  const paginatedItems = useMemo(() => {
    const start = page * PAGE_SIZE;
    return filteredItems.slice(start, start + PAGE_SIZE);
  }, [filteredItems, page]);

  const handleFiltersChange = (next: ProductFiltersState) => {
    setFilters(next);
    setPage(0);
  };

  // Helper to check if a product is in the Kuji category
  const isKujiProduct = (row: ProductWithInventory) => {
    const catName = row.product.category.name.toLowerCase();
    const catSlug = row.product.category.slug?.toLowerCase();
    // Check if it's the Kuji category or a child of Kuji
    if (catName === "kuji" || catSlug === "kuji") return true;
    // Check parent category
    const parentCat = categories?.find((c) =>
      c.children.some((child) => child.id === row.product.category.id)
    );
    if (parentCat?.name.toLowerCase() === "kuji" || parentCat?.slug?.toLowerCase() === "kuji") {
      return true;
    }
    return false;
  };

  const handleSelect = (row: ProductWithInventory) => {
    // Navigate to detail page for Kuji products (to manage prizes)
    if (row.product.hasChildren || isKujiProduct(row)) {
      router.push(`/products/${row.product.id}`);
      return;
    }
    // Open modal for regular products
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
