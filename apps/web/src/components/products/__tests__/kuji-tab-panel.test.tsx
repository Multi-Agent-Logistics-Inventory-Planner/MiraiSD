import React, { useEffect, useState } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";

const mockCustomKujiTabs = vi.fn(
  ({ items }: { items: ProductWithInventory[] }) => (
    <div data-testid="custom-kuji-tabs">{items.length}</div>
  ),
);

vi.mock("@/components/kuji", () => ({
  CustomKujiTabs: (props: { items: ProductWithInventory[] }) => mockCustomKujiTabs(props),
}));

// next/dynamic's real implementation resolves the loader asynchronously; a minimal stand-in
// that does the same (rather than eagerly requiring the module) keeps this test close to the
// page's actual runtime import behavior.
vi.mock("next/dynamic", () => ({
  default: (loader: () => Promise<{ default: React.ComponentType<unknown> }>) => {
    return function DynamicStub(props: Record<string, unknown>) {
      const [Comp, setComp] = useState<React.ComponentType<Record<string, unknown>> | null>(null);
      useEffect(() => {
        let mounted = true;
        loader().then((mod) => {
          if (mounted) setComp(() => mod.default);
        });
        return () => {
          mounted = false;
        };
      }, []);
      if (!Comp) return null;
      const C = Comp;
      return <C {...props} />;
    };
  },
}));

import { KujiTabPanel } from "../kuji-tab-panel";

const items = [{ product: { id: "p-1" } }, { product: { id: "p-2" } }] as unknown as ProductWithInventory[];

describe("KujiTabPanel", () => {
  beforeEach(() => {
    mockCustomKujiTabs.mockClear();
  });

  it("renders CustomKujiTabs at the MAIN site", async () => {
    render(<KujiTabPanel siteCode="MAIN" items={items} />);

    await waitFor(() => {
      expect(screen.getByTestId("custom-kuji-tabs")).toHaveTextContent("2");
    });
    expect(screen.queryByText(/not yet available per-site/i)).not.toBeInTheDocument();
  });

  it("renders an unavailable state at a non-MAIN site, without mounting CustomKujiTabs", async () => {
    render(<KujiTabPanel siteCode="SECOND" items={items} />);

    expect(await screen.findByText(/kuji is not yet available per-site/i)).toBeInTheDocument();
    expect(screen.queryByTestId("custom-kuji-tabs")).not.toBeInTheDocument();
    expect(mockCustomKujiTabs).not.toHaveBeenCalled();
  });

  it("renders the unavailable state while the site is still unresolved (siteCode undefined)", async () => {
    render(<KujiTabPanel siteCode={undefined} items={items} />);

    expect(await screen.findByText(/kuji is not yet available per-site/i)).toBeInTheDocument();
    expect(mockCustomKujiTabs).not.toHaveBeenCalled();
  });
});
