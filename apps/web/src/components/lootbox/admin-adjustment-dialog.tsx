"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Dialog,
  DialogContent,
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
import { getAllUsersForReviewManagement } from "@/lib/api/reviews";
import { useAdjustCoinsMutation } from "@/hooks/mutations/use-lootbox-mutations";

interface AdminAdjustmentDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialUserId?: string;
}

export function AdminAdjustmentDialog({
  open,
  onOpenChange,
  initialUserId,
}: AdminAdjustmentDialogProps) {
  const [userId, setUserId] = useState<string>(initialUserId ?? "");
  const [delta, setDelta] = useState<string>("");
  const [reason, setReason] = useState<string>("");
  const adjustMutation = useAdjustCoinsMutation();

  const usersQuery = useQuery({
    queryKey: ["users", "all"],
    queryFn: getAllUsersForReviewManagement,
    enabled: open,
    staleTime: 60_000,
  });

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setUserId(initialUserId ?? "");
      setDelta("");
      setReason("");
    }
    onOpenChange(next);
  };

  const parsedDelta = Number.parseInt(delta, 10);
  const canSubmit =
    !!userId && Number.isFinite(parsedDelta) && parsedDelta !== 0 && reason.trim().length > 0;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    try {
      await adjustMutation.mutateAsync({
        userId,
        delta: parsedDelta,
        reason: reason.trim(),
      });
      toast({
        title: `${parsedDelta > 0 ? "Granted" : "Deducted"} ${Math.abs(parsedDelta)} coin${Math.abs(parsedDelta) === 1 ? "" : "s"}.`,
        variant: "success",
      });
      handleOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to adjust coins.";
      toast({ title: "Couldn't adjust coins", description: message });
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Adjust Coins</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="adjust-user">User</Label>
            <Select value={userId} onValueChange={setUserId} disabled={usersQuery.isLoading}>
              <SelectTrigger id="adjust-user">
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
          <div className="space-y-1.5">
            <Label htmlFor="adjust-delta">
              Coins to add (use negative number to deduct)
            </Label>
            <Input
              id="adjust-delta"
              type="number"
              value={delta}
              onChange={(e) => setDelta(e.target.value)}
              placeholder="e.g. 5 or -3"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="adjust-reason">Reason (required)</Label>
            <Input
              id="adjust-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. May review bonus"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button onClick={handleSubmit} disabled={!canSubmit || adjustMutation.isPending}>
              {adjustMutation.isPending ? "Saving…" : "Apply"}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
