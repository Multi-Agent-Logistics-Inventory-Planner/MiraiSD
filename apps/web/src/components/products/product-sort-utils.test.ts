import { describe, it, expect } from "vitest";
import {
  compareProducts,
  buildParentNameMap,
  buildKujiCategoryIds,
  isKujiProduct,
  DEFAULT_PRODUCT_SORT,
} from "./product-sort-utils";
import type { ProductSort } from "./product-sort-utils";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import type { Category } from "@/types/api";

function makeRow(overrides: {
  name?: string;
  isActive?: boolean;
  categoryId?: string;
  categoryName?: string;
  totalQuantity?: number;
  hasChildren?: boolean;
  categorySlug?: string;
}): ProductWithInventory {
  return {
    product: {
      id: crypto.randomUUID(),
      name: overrides.name ?? "Test Product",
      isActive: overrides.isActive ?? true,
      hasChildren: overrides.hasChildren ?? false,
      category: {
        id: overrides.categoryId ?? "cat-1",
        name: overrides.categoryName ?? "TestCategory",
        slug: overrides.categorySlug ?? "test-category",
        parentId: null,
        displayOrder: 0,
        isActive: true,
        children: [],
        createdAt: "",
        updatedAt: "",
      },
      sku: null,
      imageUrl: null,
      parentId: null,
      createdAt: "",
      updatedAt: "",
    },
    totalQuantity: overrides.totalQuantity ?? 0,
    inventoryByLocation: [],
  } as unknown as ProductWithInventory;
}

function makeCategory(overrides: {
  id: string;
  name: string;
  slug?: string;
  children?: { id: string; name: string; slug?: string }[];
}): Category {
  return {
    id: overrides.id,
    name: overrides.name,
    slug: overrides.slug ?? overrides.name.toLowerCase(),
    parentId: null,
    displayOrder: 0,
    isActive: true,
    children: (overrides.children ?? []).map((c) => ({
      id: c.id,
      name: c.name,
      slug: c.slug ?? c.name.toLowerCase(),
      parentId: overrides.id,
      displayOrder: 0,
      isActive: true,
      children: [],
      createdAt: "",
      updatedAt: "",
    })),
    createdAt: "",
    updatedAt: "",
  };
}

describe("compareProducts", () => {
  const parentNameMap = new Map<string, string>([
    ["cat-parent", "Electronics"],
    ["cat-child", "Electronics"],
    ["cat-other", "Toys"],
  ]);

  it("sorts by product name ascending", () => {
    const a = makeRow({ name: "Apple" });
    const b = makeRow({ name: "Banana" });
    const sort: ProductSort = { column: "product", direction: "asc" };

    expect(compareProducts(a, b, sort, parentNameMap)).toBeLessThan(0);
    expect(compareProducts(b, a, sort, parentNameMap)).toBeGreaterThan(0);
  });

  it("sorts by product name descending", () => {
    const a = makeRow({ name: "Apple" });
    const b = makeRow({ name: "Banana" });
    const sort: ProductSort = { column: "product", direction: "desc" };

    expect(compareProducts(a, b, sort, parentNameMap)).toBeGreaterThan(0);
  });

  it("returns 0 for equal product names", () => {
    const a = makeRow({ name: "Same" });
    const b = makeRow({ name: "Same" });
    const sort: ProductSort = { column: "product", direction: "asc" };

    expect(compareProducts(a, b, sort, parentNameMap)).toBe(0);
  });

  it("sorts active before inactive when status ascending", () => {
    const active = makeRow({ isActive: true });
    const inactive = makeRow({ isActive: false });
    const sort: ProductSort = { column: "status", direction: "asc" };

    expect(compareProducts(active, inactive, sort, parentNameMap)).toBeLessThan(0);
  });

  it("sorts inactive before active when status descending", () => {
    const active = makeRow({ isActive: true });
    const inactive = makeRow({ isActive: false });
    const sort: ProductSort = { column: "status", direction: "desc" };

    expect(compareProducts(active, inactive, sort, parentNameMap)).toBeGreaterThan(0);
  });

  it("sorts by parent category name", () => {
    const a = makeRow({ categoryId: "cat-parent", categoryName: "Phones" });
    const b = makeRow({ categoryId: "cat-other", categoryName: "Action Figures" });
    const sort: ProductSort = { column: "category", direction: "asc" };

    // "Electronics" vs "Toys"
    expect(compareProducts(a, b, sort, parentNameMap)).toBeLessThan(0);
  });

  it("sorts by subcategory name ascending", () => {
    const a = makeRow({ categoryId: "cat-child", categoryName: "Phones" });
    const b = makeRow({ categoryId: "cat-child", categoryName: "Tablets" });
    const sort: ProductSort = { column: "subcategory", direction: "asc" };

    // Both have subcategories (parent is "Electronics", name differs)
    expect(compareProducts(a, b, sort, parentNameMap)).toBeLessThan(0);
  });

  it("sorts items with no subcategory last regardless of direction", () => {
    // "cat-parent" maps to "Electronics" and categoryName is also "Electronics" => no subcategory
    const noSub = makeRow({ categoryId: "cat-parent", categoryName: "Electronics" });
    // "cat-child" maps to "Electronics" but categoryName is "Phones" => has subcategory
    const hasSub = makeRow({ categoryId: "cat-child", categoryName: "Phones" });

    const sortAsc: ProductSort = { column: "subcategory", direction: "asc" };
    const sortDesc: ProductSort = { column: "subcategory", direction: "desc" };

    expect(compareProducts(noSub, hasSub, sortAsc, parentNameMap)).toBeGreaterThan(0);
    expect(compareProducts(noSub, hasSub, sortDesc, parentNameMap)).toBeGreaterThan(0);
  });

  it("sorts by stock ascending", () => {
    const low = makeRow({ totalQuantity: 5 });
    const high = makeRow({ totalQuantity: 100 });
    const sort: ProductSort = { column: "stock", direction: "asc" };

    expect(compareProducts(low, high, sort, parentNameMap)).toBeLessThan(0);
  });

  it("sorts by stock descending", () => {
    const low = makeRow({ totalQuantity: 5 });
    const high = makeRow({ totalQuantity: 100 });
    const sort: ProductSort = { column: "stock", direction: "desc" };

    expect(compareProducts(low, high, sort, parentNameMap)).toBeGreaterThan(0);
  });
});

describe("buildParentNameMap", () => {
  it("maps root categories to their own name", () => {
    const categories = [makeCategory({ id: "root-1", name: "Electronics" })];
    const map = buildParentNameMap(categories);

    expect(map.get("root-1")).toBe("Electronics");
  });

  it("maps child categories to their parent name", () => {
    const categories = [
      makeCategory({
        id: "root-1",
        name: "Electronics",
        children: [{ id: "child-1", name: "Phones" }],
      }),
    ];
    const map = buildParentNameMap(categories);

    expect(map.get("child-1")).toBe("Electronics");
  });

  it("returns empty map for empty input", () => {
    const map = buildParentNameMap([]);
    expect(map.size).toBe(0);
  });
});

describe("buildKujiCategoryIds", () => {
  it("returns kuji root and children IDs", () => {
    const categories = [
      makeCategory({
        id: "kuji-root",
        name: "Kuji",
        children: [
          { id: "kuji-child-1", name: "Series A" },
          { id: "kuji-child-2", name: "Series B" },
        ],
      }),
      makeCategory({ id: "other", name: "Toys" }),
    ];

    const ids = buildKujiCategoryIds(categories);
    expect(ids.has("kuji-root")).toBe(true);
    expect(ids.has("kuji-child-1")).toBe(true);
    expect(ids.has("kuji-child-2")).toBe(true);
    expect(ids.has("other")).toBe(false);
  });

  it("matches kuji case-insensitively by slug", () => {
    const categories = [
      makeCategory({ id: "k1", name: "KUJI", slug: "kuji" }),
    ];
    const ids = buildKujiCategoryIds(categories);
    expect(ids.has("k1")).toBe(true);
  });

  it("returns empty set when no kuji category exists", () => {
    const categories = [makeCategory({ id: "other", name: "Toys" })];
    const ids = buildKujiCategoryIds(categories);
    expect(ids.size).toBe(0);
  });
});

describe("isKujiProduct", () => {
  const kujiIds = new Set(["kuji-root", "kuji-child"]);

  it("returns true for products with children", () => {
    const row = makeRow({ hasChildren: true, categoryId: "other" });
    expect(isKujiProduct(row, kujiIds)).toBe(true);
  });

  it("returns true for products in kuji category", () => {
    const row = makeRow({ categoryId: "kuji-child" });
    expect(isKujiProduct(row, kujiIds)).toBe(true);
  });

  it("returns false for non-kuji products without children", () => {
    const row = makeRow({ categoryId: "other" });
    expect(isKujiProduct(row, kujiIds)).toBe(false);
  });
});

describe("DEFAULT_PRODUCT_SORT", () => {
  it("defaults to product column ascending", () => {
    expect(DEFAULT_PRODUCT_SORT).toEqual({ column: "product", direction: "asc" });
  });
});
