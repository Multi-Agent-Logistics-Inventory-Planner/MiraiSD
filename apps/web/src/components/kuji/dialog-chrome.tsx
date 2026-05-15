"use client";

import * as React from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

type DialogChromeVariant = "recordDraw" | "managePrizes";

const SIZE_BY_VARIANT: Record<DialogChromeVariant, string> = {
  recordDraw: "sm:w-[1200px] sm:h-[760px]",
  managePrizes: "md:w-[1100px] md:h-[760px]",
};

interface DialogChromeProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly variant: DialogChromeVariant;
  readonly className?: string;
  readonly children: React.ReactNode;
}

export function DialogChrome({
  open,
  onOpenChange,
  variant,
  className,
  children,
}: DialogChromeProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className={cn(
          "w-[calc(100%-2rem)] max-w-none max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden rounded-2xl bg-card",
          SIZE_BY_VARIANT[variant],
          className,
        )}
      >
        {children}
      </DialogContent>
    </Dialog>
  );
}
