"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { History, Package, Settings } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  ScrollableTabs,
  type TabConfig,
} from "@/components/ui/scrollable-tabs";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import { KujiBoxPanel } from "./kuji-box-panel";
import { KujiHistoryDialog } from "./kuji-history-dialog";

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
  const [editOpen, setEditOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const tabs = useMemo<TabConfig<string>[]>(
    () =>
      items.map((row) => ({
        value: row.product.id,
        label: row.product.name,
        icon: Package,
      })),
    [items],
  );

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

  // Resolve current tab during render: honor the user's selection when it still
  // points at a valid item, otherwise fall back to the first item. Avoids an
  // effect-driven sync that would set state during render.
  const current =
    items.find((row) => row.product.id === selected) ?? items[0]!;

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-2">
        <div className="flex-1 min-w-0">
          <ScrollableTabs
            tabs={tabs}
            value={current.product.id}
            onValueChange={setSelected}
          />
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="shrink-0 h-9"
          onClick={() => setHistoryOpen(true)}
          aria-label={`History for ${current.product.name}`}
        >
          <History className="h-4 w-4 sm:mr-1.5" />
          <span className="hidden sm:inline">History</span>
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="shrink-0 h-9"
          onClick={() => setEditOpen(true)}
          aria-label={`Edit ${current.product.name}`}
        >
          <Settings className="h-4 w-4 sm:mr-1.5" />
          <span className="hidden sm:inline">Edit Kuji</span>
        </Button>
      </div>
      <KujiBoxPanel
        key={current.product.id}
        productId={current.product.id}
        productName={current.product.name}
      />
      <ProductForm
        open={editOpen}
        onOpenChange={setEditOpen}
        initialProductId={current.product.id}
      />
      <KujiHistoryDialog
        open={historyOpen}
        onOpenChange={setHistoryOpen}
        productId={current.product.id}
        productName={current.product.name}
      />
    </div>
  );
}
