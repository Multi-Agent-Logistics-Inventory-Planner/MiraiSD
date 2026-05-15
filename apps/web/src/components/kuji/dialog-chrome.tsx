"use client";

import * as React from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

type DialogChromeVariant = "recordDraw" | "managePrizes";

const SIZE_BY_VARIANT: Record<DialogChromeVariant, string> = {
  recordDraw: "sm:w-[1200px] sm:h-[760px]",
  managePrizes: "md:w-[1280px] md:h-[800px]",
};

interface DialogChromeProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly variant: DialogChromeVariant;
  readonly className?: string;
  readonly closeOnInteractOutside?: boolean;
  readonly children: React.ReactNode;
}

export function DialogChrome({
  open,
  onOpenChange,
  variant,
  className,
  closeOnInteractOutside = true,
  children,
}: DialogChromeProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        onInteractOutside={(e) => {
          if (!closeOnInteractOutside) e.preventDefault();
        }}
        className={cn(
          "w-[calc(100%-2rem)] max-w-none sm:max-w-none md:max-w-none",
          "h-[calc(100vh-2rem)] max-h-[calc(100vh-2rem)] sm:max-h-[90vh]",
          "flex flex-col gap-0 p-0 overflow-hidden rounded-2xl bg-card",
          SIZE_BY_VARIANT[variant],
          className,
        )}
      >
        {children}
      </DialogContent>
    </Dialog>
  );
}

interface DialogCloseButtonProps {
  readonly onClose: () => void;
}

export function DialogCloseButton({ onClose }: DialogCloseButtonProps) {
  return (
    <button
      type="button"
      onClick={onClose}
      aria-label="Close"
      className={cn(
        "inline-flex items-center justify-center transition-colors",
        // Mobile: bordered 32x32 with cardAlt bg
        "h-8 w-8 rounded-lg border border-border bg-accent text-foreground hover:bg-accent/80",
        // Desktop: plain icon button 28x28 no border/bg
        "sm:h-7 sm:w-7 sm:rounded-md sm:border-0 sm:bg-transparent sm:text-muted-foreground sm:hover:text-foreground sm:hover:bg-transparent",
      )}
    >
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
        <path
          d="M3 3l8 8M11 3l-8 8"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
      </svg>
    </button>
  );
}
