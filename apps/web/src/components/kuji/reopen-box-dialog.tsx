"use client";

import { Loader2 } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useReopenKujiBoxMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { KujiBox } from "@/types/api";

interface ReopenBoxDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
}

export function ReopenBoxDialog({
  open,
  onOpenChange,
  box,
}: ReopenBoxDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const reopenBox = useReopenKujiBoxMutation();

  async function handleConfirm() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    try {
      await reopenBox.mutateAsync({
        boxId: box.id,
        actorId,
        productId: box.productId,
      });
      toast({ title: "Box reopened", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to reopen";
      toast({ title: "Reopen failed", description: message });
    }
  }

  const isPending = reopenBox.isPending;

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Reopen this kuji box?</AlertDialogTitle>
          <AlertDialogDescription>
            Reopening will set the box back to OPEN status. Linked-product
            transfer-out actions performed during close will need to be
            reconciled manually if any inventory was already moved.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending}>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={handleConfirm} disabled={isPending}>
            {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Reopen Box
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
