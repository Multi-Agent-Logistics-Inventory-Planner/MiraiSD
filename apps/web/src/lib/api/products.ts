import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { Product, ProductRequest } from "@/types/api";

const BASE_PATH = "/api/products";

/**
 * Get all products
 */
export async function getProducts(): Promise<Product[]> {
  return apiGet<Product[]>(BASE_PATH);
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
