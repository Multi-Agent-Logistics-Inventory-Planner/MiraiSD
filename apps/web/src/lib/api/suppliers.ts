import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { Product, Supplier, SupplierRequest } from "@/types/api";

const BASE_PATH = "/api/suppliers";

export interface GetSuppliersOptions {
  q?: string;
  active?: boolean;
}

/**
 * Get all suppliers with optional filtering
 */
export async function getSuppliers(
  options?: GetSuppliersOptions
): Promise<Supplier[]> {
  const params = new URLSearchParams();
  if (options?.q) params.set("q", options.q);
  if (options?.active !== undefined) params.set("active", String(options.active));
  const qs = params.toString();
  return apiGet<Supplier[]>(`${BASE_PATH}${qs ? `?${qs}` : ""}`);
}

/**
 * Get a supplier by ID
 */
export async function getSupplierById(id: string): Promise<Supplier> {
  return apiGet<Supplier>(`${BASE_PATH}/${id}`);
}

/**
 * Search suppliers by name (for autocomplete)
 */
export async function searchSuppliers(query: string): Promise<Supplier[]> {
  if (!query.trim()) return [];
  return getSuppliers({ q: query, active: true });
}

/**
 * Create a new supplier
 */
export async function createSupplier(data: SupplierRequest): Promise<Supplier> {
  return apiPost<Supplier, SupplierRequest>(BASE_PATH, data);
}

/**
 * Update an existing supplier
 */
export async function updateSupplier(
  id: string,
  data: SupplierRequest
): Promise<Supplier> {
  return apiPut<Supplier, SupplierRequest>(`${BASE_PATH}/${id}`, data);
}

/**
 * Delete (soft-delete/deactivate) a supplier
 */
export async function deleteSupplier(id: string): Promise<void> {
  return apiDelete<void>(`${BASE_PATH}/${id}`);
}

/**
 * Bulk assign products to a supplier
 */
export async function bulkAssignProducts(
  supplierId: string,
  productIds: string[]
): Promise<number> {
  return apiPost<number, { productIds: string[] }>(
    `${BASE_PATH}/${supplierId}/assign-products`,
    { productIds }
  );
}

/**
 * Get products assigned to a supplier
 */
export async function getSupplierProducts(supplierId: string): Promise<Product[]> {
  return apiGet<Product[]>(`${BASE_PATH}/${supplierId}/products`);
}
