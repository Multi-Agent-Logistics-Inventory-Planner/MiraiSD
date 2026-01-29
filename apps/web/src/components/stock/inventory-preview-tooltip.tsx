"use client";

import Image from "next/image";
import { useState } from "react";
import { ImageOff, Info } from "lucide-react";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import type { Inventory, LocationType } from "@/types/api";
import { LOCATION_TYPE_CODES } from "@/types/transfer";
import { getSafeImageUrl } from "@/lib/utils/validation";

interface InventoryPreviewTooltipProps {
  locationType: LocationType;
  locationCode: string;
  inventory: Inventory[];
  isLoading: boolean;
}

function InventoryItemImage({ src, alt }: { src: string | undefined; alt: string }) {
  const [error, setError] = useState(false);
  const safeUrl = getSafeImageUrl(src);

  if (!safeUrl || error) {
    return (
      <div className="h-6 w-6 rounded bg-muted shrink-0 flex items-center justify-center">
        <ImageOff className="h-3 w-3 text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="relative h-6 w-6 rounded shrink-0 overflow-hidden">
      <Image
        src={safeUrl}
        alt={alt}
        fill
        sizes="24px"
        className="object-cover"
        onError={() => setError(true)}
      />
    </div>
  );
}

export function InventoryPreviewTooltip({
  locationType,
  locationCode,
  inventory,
  isLoading,
}: InventoryPreviewTooltipProps) {
  const locationLabel = `${LOCATION_TYPE_CODES[locationType]}${locationCode}`;

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          className="inline-flex cursor-pointer text-muted-foreground hover:text-foreground transition-colors"
          aria-label={`Preview inventory at ${locationLabel}`}
        >
          <Info className="h-3.5 w-3.5" />
        </button>
      </TooltipTrigger>
      <TooltipContent
        side="top"
        align="start"
        sideOffset={4}
        hideArrow
        className="w-72 p-0 bg-background text-foreground border shadow-lg rounded-md"
      >
        <div className="px-3 py-2 border-b bg-muted/50 rounded-t-md">
          <p className="text-xs font-medium">Inventory at {locationLabel}</p>
          <p className="text-xs text-muted-foreground">
            {inventory.length} {inventory.length === 1 ? "product" : "products"}
          </p>
        </div>
        {isLoading ? (
          <div className="p-2 space-y-2">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : inventory.length === 0 ? (
          <div className="p-3 text-xs text-muted-foreground text-center">
            No products at this location
          </div>
        ) : (
          <ScrollArea className="max-h-48">
            <div className="p-2 space-y-1">
              {inventory.map((inv) => (
                <div
                  key={inv.id}
                  className="flex items-center gap-2 px-2 py-1.5 rounded-md bg-muted/50 text-xs"
                >
                  <InventoryItemImage src={inv.item.imageUrl} alt={inv.item.name} />
                  <span className="truncate font-medium flex-1">
                    {inv.item.name}
                  </span>
                  <span className="shrink-0 text-muted-foreground">
                    x{inv.quantity}
                  </span>
                </div>
              ))}
            </div>
          </ScrollArea>
        )}
      </TooltipContent>
    </Tooltip>
  );
}
