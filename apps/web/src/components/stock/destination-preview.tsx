"use client";

import { useState } from "react";
import Image from "next/image";
import { ImageOff } from "lucide-react";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PRODUCT_CATEGORY_LABELS, type Inventory } from "@/types/api";

interface DestinationPreviewProps {
  inventory: Inventory[];
  isLoading: boolean;
}

function DestinationItem({ inventory }: { inventory: Inventory }) {
  const [imageError, setImageError] = useState(false);
  const item = inventory.item;
  const hasImage = item.imageUrl && !imageError;

  return (
    <div className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted/50">
      <div className="relative h-8 w-8 flex-shrink-0 rounded overflow-hidden bg-muted">
        {hasImage ? (
          <Image
            src={item.imageUrl!}
            alt={item.name}
            fill
            sizes="32px"
            className="object-cover"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <ImageOff className="h-4 w-4 text-muted-foreground" />
          </div>
        )}
      </div>
      <div className="flex items-center gap-2 min-w-0 flex-1">
        <span className="text-sm text-muted-foreground truncate">
          {item.name}
        </span>
        <Badge variant="secondary" className="text-xs flex-shrink-0">
          {PRODUCT_CATEGORY_LABELS[item.category]}
        </Badge>
      </div>
      <span className="text-sm font-medium text-muted-foreground">
        {inventory.quantity}
      </span>
    </div>
  );
}

export function DestinationPreview({
  inventory,
  isLoading,
}: DestinationPreviewProps) {
  if (isLoading) {
    return (
      <div className="space-y-2 p-2">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-3/4" />
      </div>
    );
  }

  if (inventory.length === 0) {
    return (
      <div className="p-3 text-sm text-muted-foreground text-center border border-dashed rounded-md">
        No inventory at this location
      </div>
    );
  }

  return (
    <ScrollArea className="max-h-48 rounded-md border">
      <div className="p-2 space-y-1">
        {inventory.map((inv) => (
          <DestinationItem key={inv.id} inventory={inv} />
        ))}
      </div>
    </ScrollArea>
  );
}
