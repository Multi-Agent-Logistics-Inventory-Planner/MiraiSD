"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getSuppliers,
  getSupplierById,
  getSupplierProducts,
  searchSuppliers,
  GetSuppliersOptions,
} from "@/lib/api/suppliers";

/**
 * Query key factory for suppliers
 */
export const supplierKeys = {
  all: ["suppliers"] as const,
  lists: () => [...supplierKeys.all, "list"] as const,
  list: (filters?: GetSuppliersOptions) =>
    [...supplierKeys.lists(), filters] as const,
  details: () => [...supplierKeys.all, "detail"] as const,
  detail: (id: string) => [...supplierKeys.details(), id] as const,
  search: (query: string) => [...supplierKeys.all, "search", query] as const,
  products: (id: string) => [...supplierKeys.all, "products", id] as const,
};

/**
 * Fetch all suppliers with optional filtering
 */
export function useSuppliers(options?: GetSuppliersOptions) {
  return useQuery({
    queryKey: supplierKeys.list(options),
    queryFn: () => getSuppliers(options),
    staleTime: 2 * 60 * 1000, // Suppliers change infrequently
    select: (data) =>
      [...data].sort((a, b) => a.displayName.localeCompare(b.displayName)),
  });
}

/**
 * Fetch active suppliers only (for dropdowns and autocomplete)
 */
export function useActiveSuppliers() {
  return useSuppliers({ active: true });
}

/**
 * Fetch a single supplier by ID
 */
export function useSupplier(id: string | undefined) {
  return useQuery({
    queryKey: supplierKeys.detail(id!),
    queryFn: () => getSupplierById(id!),
    enabled: !!id,
    staleTime: 2 * 60 * 1000,
  });
}

/**
 * Search suppliers by name (debounced in component)
 */
export function useSupplierSearch(query: string) {
  return useQuery({
    queryKey: supplierKeys.search(query),
    queryFn: () => searchSuppliers(query),
    enabled: query.length >= 1,
    staleTime: 30 * 1000, // Search results are more dynamic
  });
}

/**
 * Fetch products assigned to a supplier
 */
export function useSupplierProducts(supplierId: string | null) {
  return useQuery({
    queryKey: supplierKeys.products(supplierId!),
    queryFn: () => getSupplierProducts(supplierId!),
    enabled: !!supplierId,
    staleTime: 60 * 1000,
  });
}
