"use client";

import { useState, useCallback, useEffect } from "react";
import Image from "next/image";
import useEmblaCarousel from "embla-carousel-react";
import { ImageOff, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";

export interface ProductImageItem {
  id: string;
  name: string;
  imageUrl: string | null | undefined;
  quantity?: number;
}

interface ProductImageCarouselProps {
  items: ProductImageItem[];
  className?: string;
}

function ProductImagePlaceholder({ quantity }: { quantity?: number }) {
  return (
    <div className="relative w-12 h-12 shrink-0 rounded-md bg-muted flex items-center justify-center">
      <ImageOff className="h-5 w-5 text-muted-foreground" />
      {quantity !== undefined && quantity > 0 && (
        <span className="absolute -bottom-1 -right-1 text-[10px] font-medium bg-background border rounded-full px-1 min-w-[18px] text-center">
          {quantity > 99 ? "99+" : quantity}
        </span>
      )}
    </div>
  );
}

function ProductImageThumbnail({
  item,
  safeUrl,
}: {
  item: ProductImageItem;
  safeUrl: string | undefined;
}) {
  const [hasError, setHasError] = useState(false);

  if (!safeUrl || hasError) {
    return <ProductImagePlaceholder quantity={item.quantity} />;
  }

  return (
    <div className="relative w-12 h-12 shrink-0 rounded-md overflow-hidden bg-muted">
      <Image
        src={safeUrl}
        alt={item.name}
        fill
        sizes="48px"
        className="object-cover"
        onError={() => setHasError(true)}
      />
      {item.quantity !== undefined && item.quantity > 0 && (
        <span className="absolute -bottom-1 -right-1 text-[10px] font-medium bg-background border rounded-full px-1 min-w-[18px] text-center">
          {item.quantity > 99 ? "99+" : item.quantity}
        </span>
      )}
    </div>
  );
}

export function ProductImageCarousel({
  items,
  className,
}: ProductImageCarouselProps) {
  const [emblaRef, emblaApi] = useEmblaCarousel({
    dragFree: true,
    containScroll: "trimSnaps",
    align: "start",
  });

  const [canScrollPrev, setCanScrollPrev] = useState(false);
  const [canScrollNext, setCanScrollNext] = useState(false);

  // Track emblaApi to sync scroll state during render (avoids useEffect setState)
  const [prevEmblaApi, setPrevEmblaApi] = useState(emblaApi);
  if (emblaApi !== prevEmblaApi) {
    setPrevEmblaApi(emblaApi);
    if (emblaApi) {
      setCanScrollPrev(emblaApi.canScrollPrev());
      setCanScrollNext(emblaApi.canScrollNext());
    }
  }

  const onSelect = useCallback(() => {
    if (!emblaApi) return;
    setCanScrollPrev(emblaApi.canScrollPrev());
    setCanScrollNext(emblaApi.canScrollNext());
  }, [emblaApi]);

  // Subscribe to embla events for ongoing updates
  useEffect(() => {
    if (!emblaApi) return;
    emblaApi.on("select", onSelect);
    emblaApi.on("reInit", onSelect);
    return () => {
      emblaApi.off("select", onSelect);
      emblaApi.off("reInit", onSelect);
    };
  }, [emblaApi, onSelect]);

  const scrollPrev = useCallback(() => {
    emblaApi?.scrollPrev();
  }, [emblaApi]);

  const scrollNext = useCallback(() => {
    emblaApi?.scrollNext();
  }, [emblaApi]);

  if (items.length === 0) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <ImageOff className="h-4 w-4" />
        <span>No items</span>
      </div>
    );
  }

  const showNavigation = items.length > 5;

  return (
    <div className={cn("relative group", className)}>
      <div className="overflow-hidden" ref={emblaRef}>
        <div className="flex gap-2 pb-2">
          {items.map((item) => {
            const safeUrl = getSafeImageUrl(item.imageUrl);
            return (
              <ProductImageThumbnail
                key={item.id}
                item={item}
                safeUrl={safeUrl}
              />
            );
          })}
        </div>
      </div>

      {showNavigation && (
        <>
          <button
            onClick={scrollPrev}
            disabled={!canScrollPrev}
            className={cn(
              "absolute left-0 top-1/2 -translate-y-1/2 -translate-x-2",
              "h-8 w-8 rounded-full bg-background/90 border shadow-sm",
              "flex items-center justify-center",
              "opacity-0 group-hover:opacity-100 transition-opacity",
              "disabled:opacity-0"
            )}
            aria-label="Previous items"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            onClick={scrollNext}
            disabled={!canScrollNext}
            className={cn(
              "absolute right-0 top-1/2 -translate-y-1/2 translate-x-2",
              "h-8 w-8 rounded-full bg-background/90 border shadow-sm",
              "flex items-center justify-center",
              "opacity-0 group-hover:opacity-100 transition-opacity",
              "disabled:opacity-0"
            )}
            aria-label="Next items"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </>
      )}
    </div>
  );
}
