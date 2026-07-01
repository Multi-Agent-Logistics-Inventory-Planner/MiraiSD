"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Package, Settings } from "lucide-react";
import { UserRole } from "@/types/api";
import { Button } from "@/components/ui/button";
import {
  ScrollableTabs,
  type TabConfig,
} from "@/components/ui/scrollable-tabs";
import { usePermissions } from "@/hooks/use-permissions";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import { KujiBoxPanel } from "./kuji-box-panel";
import { KujiHistoryView } from "./kuji-history-view";
import { OpenBoxDialog } from "./open-box-dialog";

const ProductForm = dynamic(
  () =>
    import("@/components/products/product-form").then((m) => ({
      default: m.ProductForm,
    })),
  { ssr: false },
);

interface CustomKujiTabsProps {
  readonly items: readonly ProductWithInventory[];
}

export function CustomKujiTabs({ items }: CustomKujiTabsProps) {
  const [selected, setSelected] = useState<string | null>(null);
  const [statusTab, setStatusTab] = useState<"active" | "closed">("active");
  const { role } = usePermissions();
  const canStructural = role === UserRole.ADMIN || role === UserRole.ASSISTANT_MANAGER;
  const [editOpen, setEditOpen] = useState(false);
  const [openBoxOpen, setOpenBoxOpen] = useState(false);

  const activeItems = useMemo(
    () => items.filter((row) => row.product.hasActiveBox),
    [items],
  );
  const closedItems = useMemo(
    () => items.filter((row) => !row.product.hasActiveBox),
    [items],
  );

  const visibleItems = statusTab === "active" ? activeItems : closedItems;

  const tabs = useMemo<TabConfig<string>[]>(
    () =>
      visibleItems.map((row) => ({
        value: row.product.id,
        label: row.product.name,
        icon: Package,
      })),
    [visibleItems],
  );

  const current =
    visibleItems.find((row) => row.product.id === selected) ??
    visibleItems[0] ??
    null;

  if (items.length === 0) {
    return (
      <div className="rounded-xl border bg-card p-8 text-center dark:border-none">
        <Package className="mx-auto h-8 w-8 text-muted-foreground/40" />
        <p className="mt-2 text-sm text-muted-foreground">
          No custom kuji products yet.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Status tab strip */}
      <div className="flex items-center gap-1 border-b">
        <button
          type="button"
          onClick={() => {
            setStatusTab("active");
            setSelected(null);
          }}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            statusTab === "active"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Active
          {activeItems.length > 0 ? (
            <span className="ml-1.5 text-xs text-muted-foreground">
              ({activeItems.length})
            </span>
          ) : null}
        </button>
        <button
          type="button"
          onClick={() => {
            setStatusTab("closed");
            setSelected(null);
          }}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            statusTab === "closed"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Closed
          {closedItems.length > 0 ? (
            <span className="ml-1.5 text-xs text-muted-foreground">
              ({closedItems.length})
            </span>
          ) : null}
        </button>
      </div>

      {visibleItems.length === 0 ? (
        <div className="rounded-xl border bg-card p-8 text-center dark:border-none">
          <Package className="mx-auto h-8 w-8 text-muted-foreground/40" />
          <p className="mt-2 text-sm text-muted-foreground">
            {statusTab === "active"
              ? "No active kuji boxes right now."
              : "No closed kuji products."}
          </p>
        </div>
      ) : (
        <>
          <div className="flex-1 min-w-0">
            <ScrollableTabs
              tabs={tabs}
              value={current?.product.id ?? ""}
              onValueChange={setSelected}
            />
          </div>

          {/* Closed tab: action buttons on their own row for clarity */}
          {current && statusTab === "closed" && canStructural ? (
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-9"
                onClick={() => setOpenBoxOpen(true)}
                aria-label={`Open new box for ${current.product.name}`}
              >
                Open Box
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-9"
                onClick={() => setEditOpen(true)}
                aria-label={`Edit ${current.product.name}`}
              >
                <Settings className="h-4 w-4 sm:mr-1.5" />
                <span className="hidden sm:inline">Edit Kuji</span>
              </Button>
            </div>
          ) : null}

          {current ? (
            statusTab === "active" ? (
              <KujiBoxPanel
                key={current.product.id}
                productId={current.product.id}
                productName={current.product.name}
                onEdit={canStructural ? () => setEditOpen(true) : undefined}
              />
            ) : (
              <KujiHistoryView
                key={current.product.id}
                productId={current.product.id}
                productName={current.product.name}
              />
            )
          ) : null}

          {current ? (
            <ProductForm
              open={editOpen}
              onOpenChange={setEditOpen}
              initialProductId={current.product.id}
            />
          ) : null}

          {current && statusTab === "closed" ? (
            <OpenBoxDialog
              open={openBoxOpen}
              onOpenChange={setOpenBoxOpen}
              productId={current.product.id}
              productName={current.product.name}
            />
          ) : null}
        </>
      )}
    </div>
  );
}
