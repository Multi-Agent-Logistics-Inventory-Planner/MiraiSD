"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getProducts,
  getProductWithChildren,
  getProductChildren,
  getProductById,
} from "@/lib/api/products";

export function useProducts(rootOnly = false) {
  return useQuery({
    queryKey: ["products", { rootOnly }],
    queryFn: () => getProducts(rootOnly),
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
