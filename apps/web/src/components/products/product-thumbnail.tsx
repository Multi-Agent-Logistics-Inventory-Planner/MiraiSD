"use client";

import { memo, useState } from "react";
import Image from "next/image";
import { ImageOff, Package } from "lucide-react";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";
import { Badge } from "@/components/ui/badge";

type ThumbnailSize = "sm" | "md" | "lg";

const SIZE_CONFIG: Record<ThumbnailSize, { container: string; icon: string; sizes: string }> = {
  sm: { container: "h-8 w-8", icon: "h-4 w-4", sizes: "32px" },
  md: { container: "h-10 w-10", icon: "h-5 w-5", sizes: "40px" },
  lg: { container: "h-12 w-12", icon: "h-6 w-6", sizes: "48px" },
};

type FallbackVariant = "text" | "icon" | "package";

interface ProductThumbnailProps {
  imageUrl: string | null | undefined;
  alt: string;
  size?: ThumbnailSize;
  fallbackVariant?: FallbackVariant;
  fallbackText?: string;
  badge?: number;
  className?: string;
}

export const ProductThumbnail = memo(function ProductThumbnail({
  imageUrl,
  alt,
  size = "md",
  fallbackVariant = "text",
  fallbackText = "N/A",
  badge,
  className,
}: ProductThumbnailProps) {
  const [hasError, setHasError] = useState(false);
  const safeImageUrl = getSafeImageUrl(imageUrl);
  const config = SIZE_CONFIG[size];

  const showFallback = !safeImageUrl || hasError;

  const renderFallback = () => {
    switch (fallbackVariant) {
      case "icon":
        return <ImageOff className={cn(config.icon, "text-muted-foreground")} />;
      case "package":
        return <Package className={cn(config.icon, "text-muted-foreground")} />;
      case "text":
      default:
        return (
          <span className="text-xs text-muted-foreground">{fallbackText}</span>
        );
    }
  };

  return (
    <div
      className={cn(
        "relative shrink-0 overflow-hidden rounded-md bg-muted",
        config.container,
        className
      )}
    >
      {showFallback ? (
        <div className="flex h-full w-full items-center justify-center">
          {renderFallback()}
        </div>
      ) : (
        <Image
          src={safeImageUrl}
          alt={alt}
          fill
          className="object-cover"
          sizes={config.sizes}
          onError={() => setHasError(true)}
        />
      )}
      {badge !== undefined && badge > 1 && (
        <Badge
          variant="secondary"
          className="absolute -bottom-1 -right-1 h-4 min-w-4 px-1 text-[10px]"
        >
          +{badge - 1}
        </Badge>
      )}
    </div>
  );
});
