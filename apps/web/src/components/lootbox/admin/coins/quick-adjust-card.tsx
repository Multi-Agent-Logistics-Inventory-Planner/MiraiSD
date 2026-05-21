"use client";

import { useState } from "react";
import { Minus, Plus } from "lucide-react";
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

type Sign = "+" | "-";

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
  const [sign, setSign] = useState<Sign>("+");
  const adjustMutation = useAdjustCoinsMutation();

  const usersQuery = useQuery({
    queryKey: ["users", "all"],
    queryFn: getAllUsersForReviewManagement,
    staleTime: 60_000,
  });

  const parsedAmount = Number.parseInt(amount, 10);
  const validAmount = Number.isFinite(parsedAmount) && parsedAmount > 0;
  const canSubmit = !!userId && reason.trim().length > 0 && validAmount;
  const signedDelta = sign === "+" ? parsedAmount : -parsedAmount;
  const buttonLabel = validAmount
    ? `Apply ${sign === "+" ? "+" : "−"}${parsedAmount} coin${parsedAmount === 1 ? "" : "s"}`
    : "Apply";

  const handleSubmit = async () => {
    if (!canSubmit) return;
    const trimmedReason = reason.trim();
    try {
      await adjustMutation.mutateAsync({
        userId,
        delta: signedDelta,
        reason: trimmedReason,
      });
      const userName =
        (usersQuery.data ?? []).find((u) => u.id === userId)?.fullName ?? "user";
      onAdjusted?.({
        userId,
        userName,
        delta: signedDelta,
        reason: trimmedReason,
        at: new Date().toISOString(),
      });
      toast({
        title: `${signedDelta > 0 ? "Granted" : "Deducted"} ${Math.abs(signedDelta)} coin${Math.abs(signedDelta) === 1 ? "" : "s"}.`,
        variant: "success",
      });
      setReason("");
      setAmount("");
      setSign("+");
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
            <Label className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-muted-foreground">
              Amount
            </Label>
            <div className="flex items-center gap-1.5">
              <Button
                type="button"
                size="icon"
                variant={sign === "-" ? "default" : "outline"}
                className="h-9 w-9"
                aria-label="Deduct"
                onClick={() => setSign("-")}
              >
                <Minus className="h-4 w-4" />
              </Button>
              <Input
                type="number"
                min="0"
                inputMode="numeric"
                value={amount}
                onChange={(e) => {
                  const v = e.target.value;
                  // Strip any leading minus; sign is implied by the buttons.
                  setAmount(v.replace(/^-/, ""));
                }}
                className="text-center font-mono tabular-nums"
                placeholder="0"
              />
              <Button
                type="button"
                size="icon"
                variant={sign === "+" ? "default" : "outline"}
                className="h-9 w-9"
                aria-label="Add"
                onClick={() => setSign("+")}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>
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
