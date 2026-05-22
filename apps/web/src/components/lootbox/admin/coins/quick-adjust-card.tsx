"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
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
import { getAllUsersForReviewManagement } from "@/lib/api/reviews";
import { useAdjustCoinsMutation } from "@/hooks/mutations/use-lootbox-mutations";

interface QuickAdjustCardProps {
  readonly onAdjusted?: (adjustment: {
    userId: string;
    userName: string;
    delta: number;
    reason: string;
    at: string;
  }) => void;
}

export function QuickAdjustCard({ onAdjusted }: QuickAdjustCardProps) {
  const [userId, setUserId] = useState("");
  const [reason, setReason] = useState("");
  const [amount, setAmount] = useState("");
  const adjustMutation = useAdjustCoinsMutation();

  const usersQuery = useQuery({
    queryKey: ["users", "all"],
    queryFn: getAllUsersForReviewManagement,
    staleTime: 60_000,
  });

  const parsedAmount = Number.parseInt(amount, 10);
  const validAmount = Number.isFinite(parsedAmount) && parsedAmount !== 0;
  const canSubmit = !!userId && reason.trim().length > 0 && validAmount;
  const buttonLabel = validAmount
    ? `Apply ${parsedAmount > 0 ? "+" : "−"}${Math.abs(parsedAmount)} coin${Math.abs(parsedAmount) === 1 ? "" : "s"}`
    : "Apply";

  const handleSubmit = async () => {
    if (!canSubmit) return;
    const trimmedReason = reason.trim();
    try {
      await adjustMutation.mutateAsync({
        userId,
        delta: parsedAmount,
        reason: trimmedReason,
      });
      const userName =
        (usersQuery.data ?? []).find((u) => u.id === userId)?.fullName ?? "user";
      onAdjusted?.({
        userId,
        userName,
        delta: parsedAmount,
        reason: trimmedReason,
        at: new Date().toISOString(),
      });
      toast({
        title: `${parsedAmount > 0 ? "Granted" : "Deducted"} ${Math.abs(parsedAmount)} coin${Math.abs(parsedAmount) === 1 ? "" : "s"}.`,
        variant: "success",
      });
      setReason("");
      setAmount("");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to adjust coins.";
      toast({ title: "Couldn't adjust coins", description: message });
    }
  };

  return (
    <section className="rounded-xl border border-border bg-card/40 p-5">
      <div className="mb-3.5 flex flex-col gap-0.5">
        <h3 className="text-[14px] font-medium text-foreground">Quick adjust</h3>
        <p className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
          Give or take coins from one user.
        </p>
      </div>
      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="quick-user" className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-muted-foreground">
            User
          </Label>
          <Select value={userId} onValueChange={setUserId} disabled={usersQuery.isLoading}>
            <SelectTrigger id="quick-user">
              <SelectValue placeholder="Select a user…" />
            </SelectTrigger>
            <SelectContent>
              {(usersQuery.data ?? []).map((u) => (
                <SelectItem key={u.id} value={u.id}>
                  {u.fullName} ({u.email})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="grid grid-cols-[1fr_180px] gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="quick-reason" className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-muted-foreground">
              Reason
            </Label>
            <Input
              id="quick-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. May review bonus"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="quick-amount" className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-muted-foreground">
              Amount
            </Label>
            <Input
              id="quick-amount"
              type="number"
              inputMode="numeric"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="text-center font-mono tabular-nums"
              placeholder="e.g. 5 or -5"
            />
          </div>
        </div>

        <div className="flex justify-end pt-1">
          <Button
            onClick={handleSubmit}
            disabled={!canSubmit || adjustMutation.isPending}
          >
            {adjustMutation.isPending ? "Saving…" : buttonLabel}
          </Button>
        </div>
      </div>
    </section>
  );
}
