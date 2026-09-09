import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { webApiClient, unwrapGeneratedResponse } from "./generated-client";
import type { components } from "@mirai/api-client";
import { Product, ProductListItem, ProductRequest } from "@/types/api";

const BASE_PATH = "/api/products";

export interface GetProductsOptions {
  rootOnly?: boolean;
  kujiOnly?: boolean;
  /** Exclude products with kujiType=CUSTOM. They live in the dedicated kuji tab. */
  excludeCustomKuji?: boolean;
}

/**
 * Get all products (optionally filter to root only, Kuji parents only, or
 * exclude CUSTOM kuji parents which live in their own tab).
 */
export async function getProducts(
  rootOnlyOrOptions: boolean | GetProductsOptions = false
): Promise<ProductListItem[]> {
  const opts: GetProductsOptions =
    typeof rootOnlyOrOptions === "boolean"
      ? { rootOnly: rootOnlyOrOptions }
      : rootOnlyOrOptions ?? {};
  const params = new URLSearchParams();
  if (opts.rootOnly) params.set("rootOnly", "true");
  if (opts.kujiOnly) params.set("kujiOnly", "true");
  if (opts.excludeCustomKuji) params.set("excludeCustomKuji", "true");
  const qs = params.toString();
  return apiGet<ProductListItem[]>(`${BASE_PATH}${qs ? `?${qs}` : ""}`);
}

/**
 * Get product by ID with children loaded (for Kuji detail page)
 */
export async function getProductWithChildren(id: string): Promise<Product> {
  return apiGet<Product>(`${BASE_PATH}/${id}/with-children`);
}

/**
 * Get children of a product
 */
export async function getProductChildren(id: string): Promise<ProductListItem[]> {
  return apiGet<ProductListItem[]>(`${BASE_PATH}/${id}/children`);
}

/**
 * Get a product by ID
 */
export async function getProductById(id: string): Promise<Product> {
  return apiGet<Product>(`${BASE_PATH}/${id}`);
}

/**
 * Get a product by SKU
 */
export async function getProductBySku(sku: string): Promise<Product> {
  return apiGet<Product>(`${BASE_PATH}/sku/${sku}`);
}

/**
 * Create a new product
 */
export async function createProduct(data: ProductRequest): Promise<Product> {
  return apiPost<Product, ProductRequest>(BASE_PATH, data);
}

/**
 * Update an existing product
 */
export async function updateProduct(
  id: string,
  data: ProductRequest
): Promise<Product> {
  return apiPut<Product, ProductRequest>(`${BASE_PATH}/${id}`, data);
}

/**
 * Delete a product
 */
export async function deleteProduct(id: string): Promise<void> {
  return apiDelete<void>(`${BASE_PATH}/${id}`);
}

// --- Site-scoped reads (phase-5d T-5) --------------------------------------
// SiteProduct carries only the fields site_products owns (isStocked, money fields,
// forecasting settings, version) - see .specs/phase-5d-catalog-v1-and-web/spec.md AC-6a. It is
// never the sole source for a product row: callers join it with getProducts() by id for
// catalog/Kuji display fields, which this endpoint does not carry.

export interface SiteProduct {
  productId: string;
  sku?: string;
  name: string;
  isStocked: boolean;
  forecastingEnabled?: boolean;
  unitCost?: number;
  msrp?: number;
  reorderPoint?: number;
  targetStockLevel?: number;
  leadTimeDays?: number;
  /** null only when the site has never carried this product (AC-2). */
  version: number | null;
}

function toSiteProduct(
  dto: components["schemas"]["SiteProductResponse"]
): SiteProduct | null {
  if (!dto.productId) {
    return null;
  }
  return {
    productId: dto.productId,
    sku: dto.sku,
    name: dto.name ?? "",
    isStocked: dto.isStocked ?? false,
    forecastingEnabled: dto.forecastingEnabled,
    unitCost: dto.unitCost,
    msrp: dto.msrp,
    reorderPoint: dto.reorderPoint,
    targetStockLevel: dto.targetStockLevel,
    leadTimeDays: dto.leadTimeDays,
    version: dto.version ?? null,
  };
}

/**
 * Get every product's effective, site-scoped view (assortment + settings) for one site.
 * A product the site has never carried still appears here (isStocked = false, global
 * fallback values) - see spec.md AC-2. Never 404s for an uncarried-but-valid product.
 */
export async function getSiteProducts(siteId: string): Promise<SiteProduct[]> {
  const result = await webApiClient.GET("/api/v1/sites/{siteId}/products", {
    params: { path: { siteId } },
  });
  const data = unwrapGeneratedResponse(result);
  return (data ?? [])
    .map(toSiteProduct)
    .filter((sp): sp is SiteProduct => sp !== null);
}
