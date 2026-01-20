"use client";

import { useQuery } from "@tanstack/react-query";
import { getProducts } from "@/lib/api/products";

export function useProducts() {
  return useQuery({
    queryKey: ["products"],
    queryFn: getProducts,
  });
}

