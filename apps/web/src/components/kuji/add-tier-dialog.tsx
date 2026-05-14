"use client";

import { useRef } from "react";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useAddKujiTierMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { AddKujiTierRequest, KujiBox } from "@/types/api";
import {
  TierDraftEditor,
  type TierDraftEditorHandle,
} from "@/components/kuji/tier-draft-editor";

interface AddTierDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
}

export function AddTierDialog({ open, onOpenChange, box }: AddTierDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const addTier = useAddKujiTierMutation();
  const editorRef = useRef<TierDraftEditorHandle>(null);

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    const processed = await editorRef.current?.submitTiers();
    if (!processed) return;

    let succeeded = 0;
    for (let i = 0; i < processed.length; i++) {
      const t = processed[i];
      const payload: AddKujiTierRequest = {
        actorId,
        label: t.label,
        linkedProductId: t.linkedProductId,
        sourceLocationId: t.sourceLocationId,
        activeCount: t.activeCount,
        inactiveCount: t.inactiveCount,
        price: t.price,
        autoCreate: t.mode === "create",
        productName: t.productName,
        productImageUrl: t.productImageUrl,
        productMsrp: t.mode === "create" ? t.price : null,
      };

      try {
        await addTier.mutateAsync({
          boxId: box.id,
          productId: box.productId,
          payload,
        });
        succeeded += 1;
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : "Failed to add tier";
        toast({
          title: `Add tier ${i + 1} failed`,
          description:
            succeeded > 0
              ? `${succeeded} tier(s) added before this failure. ${message}`
              : message,
        });
        editorRef.current?.focusTier(t.tempId);
        return;
      }
    }

    toast({
      title: succeeded === 1 ? "Tier added" : `${succeeded} tiers added`,
      variant: "success",
    });
    onOpenChange(false);
  }

  const isPending = addTier.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-3xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="px-6 py-4 border-b">
          <DialogTitle>Add Tier</DialogTitle>
          <DialogDescription>
            Add one or more prize tiers to this open box.
          </DialogDescription>
        </DialogHeader>

        <TierDraftEditor
          ref={editorRef}
          productId={box.productId}
          isPending={isPending}
          resetSignal={open}
        />

        <DialogFooter className="px-6 py-4 border-t">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={isPending}>
            {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Add tier
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
