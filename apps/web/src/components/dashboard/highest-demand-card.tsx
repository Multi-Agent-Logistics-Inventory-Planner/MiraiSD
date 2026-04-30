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
      <Card className="relative overflow-hidden rounded-2xl p-6 md:h-[504px] flex flex-col">
        <Skeleton className="h-6 w-32 rounded-full" />
        <div className="flex-1 flex items-center justify-center py-6">
          <Skeleton className="h-40 w-40 md:h-64 md:w-64 rounded-2xl" />
        </div>
        <div className="space-y-2">
          <Skeleton className="h-7 w-3/4" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      </Card>
    );
  }

  if (!product) {
    return (
      <Card className="relative overflow-hidden rounded-2xl p-6 md:h-[504px] flex flex-col">
        <Badge className="w-fit bg-muted text-muted-foreground border-0 text-xs px-3 py-1">
          <TrendingUp className="h-3 w-3 mr-1" />
          High demand
        </Badge>
        <div className="flex-1 flex flex-col items-center justify-center text-center py-6">
          <Package className="h-16 w-16 md:h-24 md:w-24 text-muted-foreground/50 mb-4" />
          <p className="text-sm text-muted-foreground">No demand data available</p>
        </div>
      </Card>
    );
  }

  const safeImageUrl = getSafeImageUrl(product.imageUrl);
  const dailyDemand = Math.abs(product.avgDailyDelta).toFixed(0);

  return (
    <Card className="relative overflow-hidden rounded-2xl p-6 md:h-[504px] flex flex-col">
      {/* Badge */}
      <Badge className="w-fit bg-brand-primary text-white border-none text-xs px-3 py-1">
        <TrendingUp className="h-3 w-3 mr-1" />
        High demand
      </Badge>

      {/* Large product image - emphasized */}
      <div className="flex-1 flex items-center justify-center py-6">
        <div className="relative h-40 w-40 md:h-72 md:w-72 rounded-2xl overflow-hidden flex items-center justify-center bg-muted dark:bg-[#1c1c1c]">
          {safeImageUrl ? (
            <Image
              src={safeImageUrl}
              alt={product.itemName}
              fill
              className="object-contain p-4"
              sizes="(max-width: 768px) 160px, 288px"
              quality={75}
              onError={(e) => {
                const target = e.target as HTMLImageElement;
                target.style.display = "none";
              }}
            />
          ) : (
            <Package className="h-20 w-20 md:h-32 md:w-32 text-muted-foreground" />
          )}
        </div>
      </div>

      {/* Product info */}
      <div className="space-y-1">
        <h4 className="text-xl md:text-2xl font-bold text-foreground truncate">
          {product.itemName}
        </h4>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <span>{dailyDemand}/day demand</span>
          <span>•</span>
          <span>{product.currentStock} in stock</span>
        </div>
      </div>
    </Card>
  );
}
