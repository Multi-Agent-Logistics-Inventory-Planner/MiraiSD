"use client";

import { useEffect, useRef } from "react";
import Image from "next/image";
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogFooter,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogAction,
  AlertDialogCancel,
} from "@/components/ui/alert-dialog";

interface AdjustmentConfirmDialogProps {
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function AdjustmentConfirmDialog({
  open,
  onConfirm,
  onCancel,
}: AdjustmentConfirmDialogProps) {
  // Track if user clicked confirm to prevent onCancel firing on dialog close
  const confirmedRef = useRef(false);

  useEffect(() => {
    if (open) {
      confirmedRef.current = false;
    }
  }, [open]);

  function handleConfirm() {
    confirmedRef.current = true;
    onConfirm();
  }

  function handleOpenChange(isOpen: boolean) {
    if (!isOpen && !confirmedRef.current) {
      onCancel();
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent className="sm:max-w-md">
        <AlertDialogHeader className="flex flex-col items-center gap-4">
          <AlertDialogTitle className="text-center">
            Are you sure this is an Adjustment?
          </AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Adjustments are for inventory corrections, damaged items, giveaways,
            etc. — not sales. If this product went to a customer, please use
            &quot;Sale&quot; instead.
          </AlertDialogDescription>
          {/* <div className="relative w-full max-w-[280px] sm:max-w-[320px] aspect-square overflow-hidden rounded-lg border border-border">
            <Image
              src="/dont-mess-up.jpg"
              alt="Lenny"
              fill
              className="object-contain"
              sizes="(max-width: 640px) 280px, 320px"
              priority
            />
          </div> */}
        </AlertDialogHeader>

        <AlertDialogFooter className="sm:justify-center gap-2 mt-4">
          <AlertDialogCancel
            onClick={onCancel}
            className="border-zinc-300 dark:border-zinc-600"
          >
            Use Sale Instead
          </AlertDialogCancel>
          <AlertDialogAction
            onClick={handleConfirm}
            className="bg-amber-600 hover:bg-amber-700 text-white dark:bg-amber-700 dark:hover:bg-amber-800"
          >
            Yes, it&apos;s an Adjustment
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
