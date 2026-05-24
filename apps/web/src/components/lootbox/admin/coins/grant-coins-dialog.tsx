"use client";

import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "@/hooks/use-toast";
import { useAdjustCoinsMutation } from "@/hooks/mutations/use-lootbox-mutations";

interface GrantCoinsDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly users: readonly { id: string; fullName: string; email: string }[];
  /** Optional preselected user (e.g. when opened from a player row in future). */
  readonly initialUserId?: string;
}

/** Coin grant/deduct dialog: user picker, reason, signed amount. */
export function GrantCoinsDialog({ open, onOpenChange, users, initialUserId }: GrantCoinsDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Grant or deduct coins</DialogTitle>
        </DialogHeader>
        {open ? (
          <GrantCoinsDialogBody
            users={users}
            initialUserId={initialUserId}
            onClose={() => onOpenChange(false)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function GrantCoinsDialogBody({
  users,
  initialUserId,
  onClose,
}: {
  readonly users: readonly { id: string; fullName: string; email: string }[];
  readonly initialUserId?: string;
  readonly onClose: () => void;
}) {
  const [userId, setUserId] = useState<string>(initialUserId ?? "");
  const [reason, setReason] = useState<string>("");
  const [amount, setAmount] = useState<string>("");
  const adjustMutation = useAdjustCoinsMutation();

  // If the preselected user changes (e.g. another row triggered the dialog while
  // the previous one was still mounting), honor it on open.
  useEffect(() => {
    if (initialUserId) setUserId(initialUserId);
  }, [initialUserId]);

  const parsedAmount = Number.parseInt(amount, 10);
  const validAmount = Number.isFinite(parsedAmount) && parsedAmount !== 0;
  const canSubmit = !!userId && reason.trim().length > 0 && validAmount && !adjustMutation.isPending;

  const submit = async () => {
    if (!canSubmit) return;
    const trimmedReason = reason.trim();
    try {
      await adjustMutation.mutateAsync({
        userId,
        delta: parsedAmount,
        reason: trimmedReason,
      });
      toast({
        title: `${parsedAmount > 0 ? "Granted" : "Deducted"} ${Math.abs(parsedAmount)} coin${Math.abs(parsedAmount) === 1 ? "" : "s"}.`,
        variant: "success",
      });
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to adjust coins.";
      toast({ title: "Couldn't adjust coins", description: message });
    }
  };

  const buttonLabel = validAmount
    ? `Apply ${parsedAmount > 0 ? "+" : "−"}${Math.abs(parsedAmount)} coin${Math.abs(parsedAmount) === 1 ? "" : "s"}`
    : "Apply";

  return (
    <>
      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="grant-user">User</Label>
          <Select value={userId} onValueChange={setUserId}>
            <SelectTrigger id="grant-user">
              <SelectValue placeholder="Select a user…" />
            </SelectTrigger>
            <SelectContent>
              {users.map((u) => (
                <SelectItem key={u.id} value={u.id}>
                  {u.fullName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="grid grid-cols-[1fr_140px] gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="grant-reason">Reason</Label>
            <Input
              id="grant-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. May review bonus"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="grant-amount">Amount</Label>
            <Input
              id="grant-amount"
              type="number"
              inputMode="numeric"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="text-center font-mono tabular-nums"
              placeholder="e.g. 5 or -5"
            />
          </div>
        </div>
      </div>
      <DialogFooter>
        <Button variant="ghost" onClick={onClose} disabled={adjustMutation.isPending}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!canSubmit}>
          {adjustMutation.isPending ? "Saving…" : buttonLabel}
        </Button>
      </DialogFooter>
    </>
  );
}
