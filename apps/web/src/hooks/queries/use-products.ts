"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getProducts,
  getProductWithChildren,
  getProductChildren,
  getProductById,
  type GetProductsOptions,
} from "@/lib/api/products";

export function useProducts(
  rootOnlyOrOptions: boolean | GetProductsOptions = false
) {
  const opts: GetProductsOptions =
    typeof rootOnlyOrOptions === "boolean"
      ? { rootOnly: rootOnlyOrOptions }
      : rootOnlyOrOptions ?? {};
  return useQuery({
    queryKey: ["products", opts],
    queryFn: () => getProducts(opts),
  });
}

export function useProductWithChildren(productId: string | null) {
  return useQuery({
    queryKey: ["products", productId, "with-children"],
    queryFn: () => getProductWithChildren(productId!),
    enabled: !!productId,
  });
}

export function useProductChildren(productId: string | null) {
  return useQuery({
    queryKey: ["products", productId, "children"],
    queryFn: () => getProductChildren(productId!),
    enabled: !!productId,
  });
}

export function useProduct(productId: string | null) {
  return useQuery({
    queryKey: ["products", productId],
    queryFn: () => getProductById(productId!),
    enabled: !!productId,
  });
}
