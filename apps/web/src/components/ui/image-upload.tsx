"use client";

import { useRef, useCallback } from "react";
import { ImagePlus, X, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface ImageUploadProps {
  displayUrl: string | null;
  isUploading: boolean;
  error: string | null;
  hasNewFile: boolean;
  onFileSelect: (file: File | null) => void;
  onClear: () => void;
  disabled?: boolean;
  className?: string;
}

export function ImageUpload({
  displayUrl,
  isUploading,
  error,
  hasNewFile,
  onFileSelect,
  onClear,
  disabled = false,
  className,
}: ImageUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleClick = useCallback(() => {
    inputRef.current?.click();
  }, []);

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0] ?? null;
      onFileSelect(file);
      // Reset input so the same file can be selected again
      e.target.value = "";
    },
    [onFileSelect]
  );

  const handleClear = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onClear();
    },
    [onClear]
  );

  return (
    <div className={cn("space-y-2", className)}>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        onChange={handleChange}
        className="hidden"
        disabled={disabled || isUploading}
      />

      {displayUrl ? (
        <div className="relative inline-block">
          <div className="relative h-24 w-24 overflow-hidden rounded-lg border bg-muted">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={displayUrl}
              alt="Product preview"
              className="h-full w-full object-cover"
            />
            {isUploading ? (
              <div className="absolute inset-0 flex items-center justify-center bg-background/80">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : null}
          </div>
          {!disabled && !isUploading ? (
            <Button
              type="button"
              variant="destructive"
              size="icon-sm"
              className="absolute -right-2 -top-2 h-6 w-6 rounded-full"
              onClick={handleClear}
            >
              <X className="h-3 w-3" />
              <span className="sr-only">Remove image</span>
            </Button>
          ) : null}
          {hasNewFile && !isUploading ? (
            <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 rounded bg-primary px-1.5 py-0.5 text-[10px] font-medium text-primary-foreground">
              New
            </span>
          ) : null}
        </div>
      ) : (
        <button
          type="button"
          onClick={handleClick}
          disabled={disabled || isUploading}
          className={cn(
            "flex h-24 w-24 flex-col items-center justify-center gap-1 rounded-lg border-2 border-dashed",
            "text-muted-foreground transition-colors",
            "hover:border-primary hover:text-primary",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
            "disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-border disabled:hover:text-muted-foreground"
          )}
        >
          {isUploading ? (
            <Loader2 className="h-6 w-6 animate-spin" />
          ) : (
            <>
              <ImagePlus className="h-6 w-6" />
              <span className="text-xs">Add image</span>
            </>
          )}
        </button>
      )}

      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}
