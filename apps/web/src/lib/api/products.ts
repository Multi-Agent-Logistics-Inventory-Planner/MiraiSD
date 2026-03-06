import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { Product, ProductRequest, ProductSummary } from "@/types/api";

const BASE_PATH = "/api/products";

/**
 * Get all products (optionally filter to root only)
 */
export async function getProducts(rootOnly = false): Promise<Product[]> {
  const params = rootOnly ? "?rootOnly=true" : "";
  return apiGet<Product[]>(`${BASE_PATH}${params}`);
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
export async function getProductChildren(id: string): Promise<ProductSummary[]> {
  return apiGet<ProductSummary[]>(`${BASE_PATH}/${id}/children`);
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
