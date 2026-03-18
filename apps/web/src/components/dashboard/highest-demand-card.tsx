"use client";

import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Package, TrendingUp } from "lucide-react";
import { getSafeImageUrl } from "@/lib/utils/validation";
import Image from "next/image";

interface HighestDemandProduct {
  itemId: string;
  itemName: string;
  itemSku?: string | null;
  imageUrl?: string | null;
  avgDailyDelta: number;
  currentStock: number;
}

interface HighestDemandCardProps {
  product: HighestDemandProduct | null;
  isLoading?: boolean;
}

export function HighestDemandCard({
  product,
  isLoading,
}: HighestDemandCardProps) {
  if (isLoading) {
    return (
      <Card className="relative overflow-hidden rounded-2xl bg-neutral-800 dark:bg-white p-6 shadow-none border-0 md:h-[504px] flex flex-col">
        <Skeleton className="h-6 w-32 bg-white/20 dark:bg-neutral-800/20 rounded-full" />
        <div className="flex-1 flex items-center justify-center py-6">
          <Skeleton className="h-40 w-40 md:h-64 md:w-64 rounded-2xl bg-white/20 dark:bg-neutral-800/20" />
        </div>
        <div className="space-y-2">
          <Skeleton className="h-7 w-3/4 bg-white/20 dark:bg-neutral-800/20" />
          <Skeleton className="h-4 w-1/2 bg-white/20 dark:bg-neutral-800/20" />
        </div>
      </Card>
    );
  }

  if (!product) {
    return (
      <Card className="relative overflow-hidden rounded-2xl bg-neutral-800 dark:bg-white p-6 shadow-none border-0 md:h-[504px] flex flex-col">
        <Badge className="w-fit bg-white/20 dark:bg-neutral-800/20 text-white dark:text-neutral-600 border-0 text-xs px-3 py-1">
          <TrendingUp className="h-3 w-3 mr-1" />
          High demand
        </Badge>
        <div className="flex-1 flex flex-col items-center justify-center text-center py-6">
          <Package className="h-16 w-16 md:h-24 md:w-24 text-white/30 dark:text-neutral-300 mb-4" />
          <p className="text-sm text-white/60 dark:text-neutral-500">No demand data available</p>
        </div>
      </Card>
    );
  }

  const safeImageUrl = getSafeImageUrl(product.imageUrl);
  const dailyDemand = Math.abs(product.avgDailyDelta).toFixed(0);

  return (
    <Card className="relative overflow-hidden rounded-2xl bg-neutral-800 dark:bg-white p-6 shadow-none border-0 md:h-[504px] flex flex-col">
      {/* Badge */}
      <Badge className="w-fit bg-[#0b66c2] dark:bg-violet-500 text-white border-0 text-xs px-3 py-1">
        <TrendingUp className="h-3 w-3 mr-1" />
        High demand
      </Badge>

      {/* Large product image - emphasized */}
      <div className="flex-1 flex items-center justify-center py-6">
        <div className="relative h-40 w-40 md:h-72 md:w-72 rounded-2xl overflow-hidden flex items-center justify-center bg-white/10 dark:bg-neutral-100">
          {safeImageUrl ? (
            <Image
              src={safeImageUrl}
              alt={product.itemName}
              fill
              className="object-contain p-4"
              onError={(e) => {
                const target = e.target as HTMLImageElement;
                target.style.display = "none";
              }}
            />
          ) : (
            <Package className="h-20 w-20 md:h-32 md:w-32 text-white/40 dark:text-neutral-400" />
          )}
        </div>
      </div>

      {/* Product info */}
      <div className="space-y-1">
        <h4 className="text-xl md:text-2xl font-bold text-white dark:text-neutral-900 truncate">
          {product.itemName}
        </h4>
        <div className="flex items-center gap-4 text-sm text-white/70 dark:text-neutral-600">
          <span>{dailyDemand}/day demand</span>
          <span>•</span>
          <span>{product.currentStock} in stock</span>
        </div>
      </div>
    </Card>
  );
}
