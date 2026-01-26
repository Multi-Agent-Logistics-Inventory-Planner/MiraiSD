"use client";

import { useRef, useCallback, useState } from "react";
import { Upload, X, Loader2 } from "lucide-react";
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
  const [isDragging, setIsDragging] = useState(false);

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

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);

      if (disabled || isUploading) return;

      const file = e.dataTransfer.files?.[0] ?? null;
      if (file && file.type.startsWith("image/")) {
        onFileSelect(file);
      }
    },
    [disabled, isUploading, onFileSelect]
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
          <div className="relative h-32 w-32 overflow-hidden rounded-lg border bg-muted">
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
        </div>
      ) : (
        <div
          onClick={handleClick}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          className={cn(
            "flex w-full cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed bg-muted/30 px-6 py-12 transition-colors",
            isDragging && "border-primary bg-primary/5",
            !isDragging && "hover:border-muted-foreground/50 hover:bg-muted/50",
            (disabled || isUploading) && "cursor-not-allowed opacity-50"
          )}
        >
          {isUploading ? (
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          ) : (
            <>
              <div className="rounded-lg bg-muted p-3">
                <Upload className="h-5 w-5 text-muted-foreground" />
              </div>
              <div className="text-center">
                <p className="text-sm">
                  <span className="font-medium text-primary">Click to upload</span>
                  <span className="text-muted-foreground"> or drag and drop</span>
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  JPG, JPEG, PNG less than 5MB.
                </p>
              </div>
            </>
          )}
        </div>
      )}

      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}
