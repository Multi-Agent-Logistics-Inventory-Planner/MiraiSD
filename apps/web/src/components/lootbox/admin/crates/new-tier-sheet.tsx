"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "@/hooks/use-toast";
import { useCreateTierMutation } from "@/hooks/mutations/use-lootbox-mutations";
import type { LootboxTier } from "@/types/lootbox";

interface NewTierSheetProps {
  readonly lootboxId: string;
  readonly existingTiers: readonly LootboxTier[];
  readonly onClose: () => void;
}

const HEX_PATTERN = /^#[0-9a-fA-F]{6}$/;

export function NewTierSheet({
  lootboxId,
  existingTiers,
  onClose,
}: NewTierSheetProps) {
  const [name, setName] = useState("");
  const [color, setColor] = useState("#8a8a93");
  const createTier = useCreateTierMutation();

  const trimmed = name.trim();
  const existingNames = new Set(
    existingTiers.map((t) => t.name.toLowerCase())
  );
  const duplicate = trimmed.length > 0 && existingNames.has(trimmed.toLowerCase());
  const validColor = HEX_PATTERN.test(color);
  const canSubmit =
    trimmed.length > 0 && !duplicate && validColor && !createTier.isPending;

  const nextSortOrder =
    existingTiers.reduce((max, t) => Math.max(max, t.sortOrder), -1) + 1;

  const submit = async () => {
    if (!canSubmit) return;
    try {
      await createTier.mutateAsync({
        lootboxId,
        name: trimmed,
        probabilityPct: 0,
        displayColor: color,
        sortOrder: nextSortOrder,
        active: false,
      });
      toast({
        title: `Tier "${trimmed}" created.`,
        description:
          "Add prizes, then assign weight in Tier weights to activate.",
        variant: "success",
      });
      onClose();
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to create tier.";
      toast({ title: "Couldn't create tier", description: message });
    }
  };

  return (
    <div className="rounded-xl border border-brand-primary/30 bg-brand-primary/[0.04] p-3.5">
      <div className="mb-2.5 font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
        Add tier
      </div>
      <div className="space-y-3">
        <div className="grid grid-cols-[1fr_120px] gap-3">
          <div className="space-y-1.5">
            <Label>Name</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={40}
              placeholder="e.g. MYTHIC"
              aria-invalid={duplicate || undefined}
            />
            {duplicate ? (
              <p className="font-mono text-[11px] text-rose-400">
                A tier with this name already exists in this box.
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label>Color</Label>
            <div className="flex items-center gap-2">
              <input
                type="color"
                value={color}
                onChange={(e) => setColor(e.target.value)}
                aria-label="Tier color"
                className="h-9 w-10 cursor-pointer rounded-md border border-border bg-card"
              />
              <Input
                value={color}
                onChange={(e) => setColor(e.target.value)}
                maxLength={7}
                className="font-mono"
                aria-invalid={!validColor || undefined}
              />
            </div>
          </div>
        </div>
        <p className="font-mono text-[11px] text-muted-foreground">
          New tiers start at 0% and inactive. Add prizes, then assign weight in
          Tier weights to activate.
        </p>
        <div className="flex items-center justify-end gap-2">
          <Button variant="ghost" size="sm" onClick={onClose}>
            Cancel
          </Button>
          <Button size="sm" onClick={submit} disabled={!canSubmit}>
            {createTier.isPending ? "Adding…" : "Add tier"}
          </Button>
        </div>
      </div>
    </div>
  );
}
