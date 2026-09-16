import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

// Radix Checkbox (via @radix-ui/react-use-size) calls ResizeObserver, which jsdom doesn't
// implement - this project's jsdom setup has no global polyfill for it (no other test in this
// repo renders a real Checkbox - checked before adding this).
if (typeof window !== "undefined" && !window.ResizeObserver) {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}

// --- Data/mutation hooks: mocked directly, this test drives onSubmit's initial-stock branch,
// not the full product-creation/category/image machinery. ---
const mockUseCurrentSite = vi.fn();
vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

const mockCreateMutateAsync = vi.fn();
vi.mock("@/hooks/mutations/use-product-mutations", () => ({
  useCreateProductMutation: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateProductMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteProductMutation: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/queries/use-products", () => ({
  useProduct: () => ({ data: null }),
}));

const CATEGORY = { id: "cat-1", name: "Toys", slug: "toys", parentId: null, displayOrder: 0, isActive: true, usesPacks: false, children: [], createdAt: "", updatedAt: "" };
// Stable array references, not created fresh per call - product-form.tsx's kuji-detection
// effect depends on `categories` (and separately calls setPendingPrizes([]) with a fresh []
// literal whenever the category isn't Kuji); returning a *new* array from this mock on every
// call would make that effect's own dependency change identity every render, re-running it and
// setting new-array state every time - an infinite render loop, not a real product bug (the
// same class of bug the useImageUpload mock above hit with `resetImage`).
const STABLE_CATEGORIES = [CATEGORY];
const STABLE_CHILD_CATEGORIES: unknown[] = [];
vi.mock("@/hooks/queries/use-categories", () => ({
  useCategories: () => ({ data: STABLE_CATEGORIES, isLoading: false }),
  useChildCategories: () => STABLE_CHILD_CATEGORIES,
}));

vi.mock("@/hooks/use-permissions", () => ({
  usePermissions: () => ({
    canViewCosts: false,
    canViewMsrp: false,
    can: () => true,
  }),
}));

// Stable function references (not created fresh per call) - product-form.tsx's own useEffect
// depends on `resetImage` (imageUpload.reset); a mock that returns a *new* vi.fn() on every
// render would change that dependency's identity every render, re-running the effect (which
// calls form.reset()/setState) forever - an infinite render loop, not a real product bug.
const stableImageUploadFns = {
  selectFile: vi.fn(),
  clear: vi.fn(),
  upload: vi.fn(),
  reset: vi.fn(),
};
vi.mock("@/hooks/use-image-upload", () => ({
  useImageUpload: () => ({
    displayUrl: null,
    isUploading: false,
    error: null,
    hasNewFile: false,
    hasImage: false,
    ...stableImageUploadFns,
  }),
}));

const mockToast = vi.fn();
vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: mockToast }),
}));

// --- Site-scoped inventory create call: this is what review finding 1 is about - a missing
// siteId must never let this call happen while still silently succeeding overall. ---
const mockResolveSiteLocationId = vi.fn();
vi.mock("@/lib/api/locations", () => ({
  resolveSiteLocationId: (...args: unknown[]) => mockResolveSiteLocationId(...args),
}));

const mockCreateSiteLocationInventory = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  createSiteLocationInventory: (...args: unknown[]) => mockCreateSiteLocationInventory(...args),
  newIdempotencyKey: () => "idem-key-1",
}));

// --- Deep subcomponents not under test here: stubbed so the test stays focused on onSubmit's
// initial-stock branch (review finding 1), not the form's other, already-covered UI. ---
vi.mock("@/components/ui/image-upload", () => ({
  ImageUpload: () => null,
}));
vi.mock("@/components/suppliers", () => ({
  SupplierAutocomplete: () => null,
}));
vi.mock("@/components/products/manage-categories-dialog", () => ({
  ManageCategoriesDialog: () => null,
}));
vi.mock("@/components/products/delete-product-dialog", () => ({
  DeleteProductDialog: () => null,
}));
vi.mock("@/components/products/prize-table-inline", () => ({
  PrizeTableInline: () => null,
}));

// LocationSelector isn't exercised by this test - initial stock keeps its default
// NOT_ASSIGNED selection - but the real component calls useLocations(), a real network hook
// with no mock here; stubbed to avoid an unmocked fetch during render.
vi.mock("@/components/stock/location-selector", () => ({
  LocationSelector: () => null,
}));

// --- Category select: the real Radix Select renders its options through a portal that only
// mounts once opened, and Radix's open/interact flow needs pointer-capture polyfills this
// project's jsdom setup doesn't provide (no other test in this repo drives the real component -
// checked before adding this). Stubbed as always-visible option buttons so this test can select
// a category without depending on that, since category selection isn't what review finding 1 is
// about. ---
vi.mock("@/components/ui/select", () => {
  const SelectCtx = React.createContext<{ onValueChange?: (v: string) => void }>({});
  function Select({
    onValueChange,
    children,
  }: {
    onValueChange?: (v: string) => void;
    children: React.ReactNode;
  }) {
    return <SelectCtx.Provider value={{ onValueChange }}>{children}</SelectCtx.Provider>;
  }
  function SelectTrigger({ children }: { children: React.ReactNode }) {
    return <>{children}</>;
  }
  function SelectValue() {
    return null;
  }
  function SelectContent({ children }: { children: React.ReactNode }) {
    return <>{children}</>;
  }
  function SelectItem({ value, children }: { value: string; children: React.ReactNode }) {
    const { onValueChange } = React.useContext(SelectCtx);
    return (
      <button type="button" onClick={() => onValueChange?.(value)}>
        {children}
      </button>
    );
  }
  return { Select, SelectTrigger, SelectValue, SelectContent, SelectItem };
});

import { ProductForm } from "../product-form";

const NEW_PRODUCT = { id: "p-new", name: "Widget", sku: "WID-1" };

function renderForm() {
  return render(
    <ProductForm open={true} onOpenChange={vi.fn()} initialProductId={null} />
  );
}

describe("ProductForm - initial stock creation (review finding 1)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateMutateAsync.mockResolvedValue(NEW_PRODUCT);
    mockResolveSiteLocationId.mockResolvedValue("loc-resolved-1");
    mockCreateSiteLocationInventory.mockResolvedValue({
      inventoryId: "inv-1",
      productId: "p-new",
      quantity: 1,
    });
  });

  async function fillNameCategoryAndStock() {
    fireEvent.change(screen.getByLabelText("Product Name"), {
      target: { value: "Widget" },
    });
    fireEvent.click(screen.getByText("Toys"));
    // "Add initial stock" is the first checkbox in the form (the forecasting toggle, the
    // other checkbox, renders further down); clicked by role to avoid depending on native
    // <label>-wraps-a-Radix-button click forwarding in jsdom.
    fireEvent.click(screen.getAllByRole("checkbox")[0]);
    fireEvent.click(await screen.findByRole("button", { name: "Increase quantity" }));
  }

  it("surfaces a destructive toast and never calls the create-inventory API when no site is active", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined });
    renderForm();

    await fillNameCategoryAndStock();
    fireEvent.click(screen.getByRole("button", { name: /add product/i }));

    await waitFor(() => expect(mockCreateMutateAsync).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Product created, but stock was not added",
          description: "No active site.",
          variant: "destructive",
        })
      )
    );

    // The whole point of this fix: the product-created toast must not be the only signal - the
    // stock-loss must never be silent, and the create-inventory call must never fire without a
    // resolved site.
    expect(mockResolveSiteLocationId).not.toHaveBeenCalled();
    expect(mockCreateSiteLocationInventory).not.toHaveBeenCalled();
  });

  it("creates the initial stock row when a site is active", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1" });
    renderForm();

    await fillNameCategoryAndStock();
    fireEvent.click(screen.getByRole("button", { name: /add product/i }));

    await waitFor(() => expect(mockCreateSiteLocationInventory).toHaveBeenCalledTimes(1));
    expect(mockResolveSiteLocationId).toHaveBeenCalledWith("site-1", "NOT_ASSIGNED", "__not_assigned__");
    expect(mockToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Initial stock added", variant: "success" })
    );
  });
});
