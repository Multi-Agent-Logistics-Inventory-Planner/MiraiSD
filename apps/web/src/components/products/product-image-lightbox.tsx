"use client";

import { useState } from "react";
import Image from "next/image";
import { ImageOff } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from "@/components/ui/dialog";
import { getSafeImageUrl } from "@/lib/utils/validation";

interface ProductImageLightboxProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  imageUrl: string | null | undefined;
  alt: string;
}

export function ProductImageLightbox({
  open,
  onOpenChange,
  imageUrl,
  alt,
}: ProductImageLightboxProps) {
  const [hasError, setHasError] = useState(false);
  const safeImageUrl = getSafeImageUrl(imageUrl);
  const showFallback = !safeImageUrl || hasError;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-black/95 border-0 p-0 max-w-[min(90vw,1080px)] sm:max-w-[min(90vw,1080px)]">
        <DialogTitle className="sr-only">{alt}</DialogTitle>
        <div className="relative aspect-square w-full max-h-[85vh]">
          {showFallback ? (
            <div className="flex h-full w-full items-center justify-center">
              <ImageOff className="h-16 w-16 text-muted-foreground" />
            </div>
          ) : (
            <Image
              src={safeImageUrl}
              alt={alt}
              fill
              sizes="(max-width: 640px) 100vw, 1080px"
              quality={75}
              priority
              className="object-contain"
              onError={() => setHasError(true)}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
