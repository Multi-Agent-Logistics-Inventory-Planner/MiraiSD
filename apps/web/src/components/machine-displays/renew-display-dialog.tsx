"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { Check, ImageOff, Loader2, RefreshCw, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";
import type { MachineDisplay, Product } from "@/types/api";

interface RenewDisplayDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentDisplays: MachineDisplay[];
  products: Product[];
  isSaving?: boolean;
  onSubmit: (displayIds: string[]) => Promise<void> | void;
}

interface DisplayItemCardProps {
  display: MachineDisplay;
  product?: Product;
  selected: boolean;
  onToggle: () => void;
  disabled?: boolean;
}

function formatDuration(startedAt: string): string {
  const start = new Date(startedAt);
  const end = new Date();
  const days = Math.floor(
    (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)
  );
  if (days === 0) return "< 1 day";
  if (days === 1) return "1 day";
  return `${days} days`;
}

function DisplayItemCard({
  display,
  product,
  selected,
  onToggle,
  disabled = false,
}: DisplayItemCardProps) {
  const [imageError, setImageError] = useState(false);

  const safeImageUrl = getSafeImageUrl(product?.imageUrl);
  const hasImage = safeImageUrl && !imageError;

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      className={cn(
        "overflow-hidden min-w-0 w-full flex items-center gap-2 sm:gap-4 py-3 sm:py-4 sm:px-3 border-b last:border-b-0 cursor-pointer",
        "transition-colors text-left",
        selected
          ? "bg-primary/5 border-l-2 border-l-primary pl-2"
          : "hover:bg-muted/50",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="relative h-12 w-12 sm:h-16 sm:w-16 shrink-0 rounded-lg overflow-hidden bg-muted">
        {hasImage ? (
          <Image
            src={safeImageUrl}
            alt={display.productName}
            fill
            sizes="(max-width: 640px) 48px, 64px"
            className="object-cover"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
          </div>
        )}
        {selected && (
          <div className="absolute inset-0 bg-primary/20 flex items-center justify-center">
            <Check className="h-6 w-6 text-primary" />
          </div>
        )}
      </div>

      <div className="w-0 flex-1 overflow-hidden">
        <p className="font-medium text-xs sm:text-base truncate">
          {display.productName}
        </p>
        <p className="text-[10px] sm:text-xs text-muted-foreground mt-1">
          On display for {formatDuration(display.startedAt)}
        </p>
      </div>

      <div className="flex flex-col items-end gap-0.5 shrink-0 pl-2">
        <Badge
          variant={display.stale ? "destructive" : "secondary"}
          className="text-[10px] sm:text-xs"
        >
          {formatDuration(display.startedAt)}
        </Badge>
      </div>
    </button>
  );
}

export function RenewDisplayDialog({
  open,
  onOpenChange,
  currentDisplays,
  products,
  isSaving,
  onSubmit,
}: RenewDisplayDialogProps) {
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const selectedDisplays = useMemo(
    () => currentDisplays.filter((d) => selectedIds.includes(d.id)),
    [currentDisplays, selectedIds]
  );

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setSelectedIds([]);
    }
  }, [open]);

  function handleToggle(displayId: string) {
    setSelectedIds((prev) =>
      prev.includes(displayId)
        ? prev.filter((id) => id !== displayId)
        : [...prev, displayId]
    );
  }

  function handleRemoveSelected(displayId: string) {
    setSelectedIds((prev) => prev.filter((id) => id !== displayId));
  }

  function handleSelectAll() {
    setSelectedIds(currentDisplays.map((d) => d.id));
  }

  async function handleSubmit() {
    if (selectedIds.length === 0) return;
    await onSubmit(selectedIds);
    setSelectedIds([]);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg h-[85dvh] max-h-[85dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle className="flex items-center gap-2">
            <RefreshCw className="h-5 w-5" />
            Renew Display History
          </DialogTitle>
          <DialogDescription>
            Select items to renew their display tracking. This will reset the
            &quot;days on display&quot; counter for the selected items without
            removing them from the machine.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col px-6 py-4">
          {currentDisplays.length === 0 ? (
            <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
              No items currently on display
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm font-medium">
                  Current Items ({currentDisplays.length})
                </p>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleSelectAll}
                  disabled={
                    Boolean(isSaving) ||
                    selectedIds.length === currentDisplays.length
                  }
                >
                  Select All
                </Button>
              </div>

              <div className="flex-1 min-h-0 w-full overflow-hidden">
                <ScrollArea className="h-full w-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
                  <div className="w-full overflow-hidden pb-2 pr-3">
                    {currentDisplays.map((display) => {
                      const product = products.find(
                        (p) => p.id === display.productId
                      );
                      return (
                        <DisplayItemCard
                          key={display.id}
                          display={display}
                          product={product}
                          selected={selectedIds.includes(display.id)}
                          onToggle={() => handleToggle(display.id)}
                          disabled={Boolean(isSaving)}
                        />
                      );
                    })}
                  </div>
                </ScrollArea>
              </div>

              {/* Selected items badges */}
              {selectedDisplays.length > 0 && (
                <div className="shrink-0 pt-3 border-t mt-3">
                  <p className="text-xs text-muted-foreground mb-2">
                    Selected ({selectedDisplays.length})
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {selectedDisplays.map((d) => (
                      <Badge key={d.id} variant="secondary" className="text-xs pr-1">
                        {d.productName}
                        <button
                          onClick={() => handleRemoveSelected(d.id)}
                          className="ml-1 hover:text-destructive"
                          disabled={Boolean(isSaving)}
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <DialogFooter className="shrink-0 border-t p-6">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={Boolean(isSaving) || selectedIds.length === 0}
          >
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Renew {selectedIds.length > 0 ? `${selectedIds.length} Item(s)` : ""}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
