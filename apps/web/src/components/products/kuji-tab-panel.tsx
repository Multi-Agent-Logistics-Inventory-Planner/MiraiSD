"use client";

import dynamic from "next/dynamic";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";

const CustomKujiTabs = dynamic(
  () =>
    import("@/components/kuji").then((m) => ({
      default: m.CustomKujiTabs,
    })),
  { ssr: false }
);

export const MAIN_SITE_CODE = "MAIN";

interface KujiTabPanelProps {
  readonly siteCode: string | undefined;
  readonly items: ProductWithInventory[];
}

/**
 * Gates the Custom Kuji tab on the current site (spec.md phase-5d AC-6e). Kuji/lootbox box
 * state has no site_id column yet - Phase 7, not this record, migrates it to per-site data. Until
 * then, rendering the tab unconditionally on a site-scoped page would present that unmigrated,
 * global data as if it belonged to whichever site is selected. This is a display gate only: no
 * backend change, no new authorization boundary. It (and this component) is removed once Phase 7
 * gives kuji real per-site state.
 */
export function KujiTabPanel({ siteCode, items }: KujiTabPanelProps) {
  if (siteCode !== MAIN_SITE_CODE) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Kuji is not yet available per-site</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Kuji box activity is not migrated to per-site data yet (Phase 7). It stays available at
          the MAIN site only until that migration.
        </CardContent>
      </Card>
    );
  }

  return <CustomKujiTabs items={items} />;
}
