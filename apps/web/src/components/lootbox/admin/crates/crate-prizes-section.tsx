"use client";

import { useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
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
import { ImageUpload } from "@/components/ui/image-upload";
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
import { useImageUpload } from "@/hooks/use-image-upload";
import { cn } from "@/lib/utils";
import { toast } from "@/hooks/use-toast";
import {
  useCreatePrizeMutation,
  useDeletePrizeMutation,
  useUpdatePrizeMutation,
} from "@/hooks/mutations/use-lootbox-mutations";
import type { LootboxPrize, LootboxTier } from "@/types/lootbox";

interface CratePrizesSectionProps {
  readonly tiers: readonly LootboxTier[];
}

export function CratePrizesSection({ tiers }: CratePrizesSectionProps) {
  const [isAdding, setIsAdding] = useState(false);
  const [tabValue, setTabValue] = useState<string>("all");
  const totalPrizes = tiers.reduce((sum, t) => sum + t.prizes.length, 0);

  // If the previously-selected tier was deleted out from under us, snap back to "All".
  const effectiveTab =
    tabValue === "all" || tiers.some((t) => t.id === tabValue) ? tabValue : "all";

  const visibleEntries: { readonly tier: LootboxTier; readonly prize: LootboxPrize }[] =
    tiers.flatMap((tier) =>
      tier.prizes
        .filter(() => effectiveTab === "all" || effectiveTab === tier.id)
        .map((prize) => ({ tier, prize }))
    );

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Prizes · <span className="tabular-nums">{totalPrizes}</span>
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setIsAdding(true)}
          disabled={tiers.length === 0 || isAdding}
        >
          <Plus className="h-4 w-4" />
          Add prize
        </Button>
      </div>

      {tiers.length > 0 ? (
        <TierTabs
          tiers={tiers}
          value={effectiveTab}
          onValueChange={setTabValue}
          totalPrizes={totalPrizes}
        />
      ) : null}

      {isAdding ? (
        <NewPrizeSheet tiers={tiers} onClose={() => setIsAdding(false)} />
      ) : null}

      {tiers.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border p-6 text-center text-[13px] text-muted-foreground">
          No tiers yet. Create a tier above, then add prizes.
        </p>
      ) : totalPrizes === 0 ? (
        <p className="rounded-xl border border-dashed border-border p-6 text-center text-[13px] text-muted-foreground">
          No prizes yet. Click <span className="font-medium">Add prize</span> to create one.
        </p>
      ) : visibleEntries.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border p-6 text-center text-[13px] text-muted-foreground">
          No prizes in this tier yet.
        </p>
      ) : (
        <ul className="space-y-2">
          {visibleEntries.map(({ tier, prize }) => (
            <PrizeCard
              key={prize.id}
              prize={prize}
              tier={tier}
              tiers={tiers}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * Pill-button tab strip for switching between "All" and each tier. Mirrors the styling
 * of ScrollableTabs (the custom-kuji tier strip) but renders the tier color as a dot
 * and inlines the tier weight so admins can see prob distribution at a glance.
 */
function TierTabs({
  tiers,
  value,
  onValueChange,
  totalPrizes,
}: {
  readonly tiers: readonly LootboxTier[];
  readonly value: string;
  readonly onValueChange: (v: string) => void;
  readonly totalPrizes: number;
}) {
  return (
    <div className="relative">
      <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />
      <div className="flex gap-1.5 overflow-x-auto scrollbar-none pb-1 pr-6">
        <TierTabButton
          active={value === "all"}
          color={null}
          label="All"
          suffix={`${totalPrizes}`}
          onClick={() => onValueChange("all")}
        />
        {tiers.map((t) => (
          <TierTabButton
            key={t.id}
            active={value === t.id}
            color={t.displayColor}
            label={t.name}
            suffix={`${Number(t.probabilityPct).toFixed(2)}%`}
            onClick={() => onValueChange(t.id)}
          />
        ))}
      </div>
    </div>
  );
}

function TierTabButton({
  active,
  color,
  label,
  suffix,
  onClick,
}: {
  readonly active: boolean;
  readonly color: string | null;
  readonly label: string;
  readonly suffix: string;
  readonly onClick: () => void;
}) {
  return (
    <Button
      type="button"
      variant={active ? "default" : "outline"}
      size="sm"
      onClick={onClick}
      className={cn(
        "shrink-0 gap-2",
        active
          ? "bg-brand-primary text-white hover:bg-brand-primary/90"
          : "bg-[#e1e1e1] dark:bg-[#30302e] border-none dark:text-[#9b9b9a]"
      )}
    >
      {color ? (
        <span
          aria-hidden
          className="inline-block h-2 w-2 rounded-full"
          style={{ backgroundColor: color }}
        />
      ) : null}
      <span>{label}</span>
      <span
        className={cn(
          "font-mono text-[10.5px] tabular-nums",
          active ? "text-white/80" : "text-muted-foreground"
        )}
      >
        {suffix}
      </span>
    </Button>
  );
}

function PrizeCard({
  prize,
  tier,
  tiers,
}: {
  readonly prize: LootboxPrize;
  readonly tier: LootboxTier;
  readonly tiers: readonly LootboxTier[];
}) {
  const updatePrize = useUpdatePrizeMutation();
  const deletePrize = useDeletePrizeMutation();
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const handleDelete = async () => {
    try {
      await deletePrize.mutateAsync({ id: prize.id });
      toast({ title: `Deleted ${prize.name}.`, variant: "success" });
      setConfirmDelete(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete prize.";
      toast({ title: "Couldn't delete prize", description: message });
    }
  };

  const handleToggleActive = async () => {
    try {
      await updatePrize.mutateAsync({
        id: prize.id,
        body: { tierId: prize.tierId, name: prize.name, active: !prize.active },
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update prize.";
      toast({ title: "Couldn't update prize", description: message });
    }
  };

  if (editing) {
    return (
      <li className="rounded-xl border border-border bg-card/40 p-3.5">
        <PrizeEditForm
          prize={prize}
          tiers={tiers}
          onClose={() => setEditing(false)}
        />
      </li>
    );
  }

  // A prize on a 0%-weight tier can't be rolled even if `prize.active` is true,
  // so we surface that as "Inactive" at the row level: faded card + Inactive
  // badge. The toggle still reflects the underlying `prize.active` flag, but
  // when the tier is inactive the badge is non-interactive (re-activating the
  // prize wouldn't change the effective state until the tier gets weight).
  const tierInactive = !tier.active;
  const effectivelyInactive = tierInactive || !prize.active;
  // Per-prize odds: prizes within a tier are uniformly drawn, so each active prize
  // gets an equal share of its tier's weight. Inactive/sold-out prizes (active=false)
  // are excluded from the divisor and show 0%.
  const activeSiblingCount = tier.prizes.filter((p) => p.active).length;
  const prizePct =
    prize.active && !tierInactive && activeSiblingCount > 0
      ? Number(tier.probabilityPct) / activeSiblingCount
      : 0;
  return (
    <li
      className={cn(
        "rounded-xl border border-border bg-card/40 p-3.5",
        effectivelyInactive && "opacity-65"
      )}
    >
      <div className="flex items-center justify-between gap-3">
        <span
          className="inline-flex justify-center rounded-full px-2.5 py-1 font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em]"
          style={{
            backgroundColor: tier.displayColor ?? "#8a8a93",
            color: pickContrastText(tier.displayColor),
          }}
        >
          {tier.name}
        </span>
        <div className="flex items-center gap-2 font-mono text-[11px] tabular-nums text-muted-foreground">
          <span title="Per-prize probability (tier weight ÷ active siblings)">
            {prizePct.toFixed(2)}%
          </span>
          <span className="opacity-60">·</span>
          <span title="Tier weight">
            tier {Number(tier.probabilityPct).toFixed(2)}%
          </span>
        </div>
      </div>
      <div className="mt-2.5 flex items-center justify-between gap-3">
        <div className="min-w-0 space-y-0.5">
          <div className="truncate text-[14px] font-medium text-foreground">
            {prize.name}
          </div>
          {prize.description ? (
            <div className="truncate font-mono text-[11px] text-muted-foreground">
              {prize.description}
            </div>
          ) : null}
        </div>
        <div className="flex items-center gap-1.5">
          <QuantityBadge quantity={prize.quantity} />
          <button
            type="button"
            onClick={handleToggleActive}
            disabled={tierInactive}
            title={
              tierInactive
                ? "Tier has 0% weight — give the tier probability before toggling prize state."
                : prize.active
                  ? "Click to deactivate this prize."
                  : "Click to activate this prize."
            }
            className={cn(
              "inline-flex items-center rounded-full px-2.5 py-1 font-mono text-[10.5px] uppercase tracking-[0.06em] transition-colors",
              effectivelyInactive
                ? "bg-card text-muted-foreground hover:bg-card/80"
                : "bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500/15",
              tierInactive && "cursor-not-allowed"
            )}
          >
            {effectivelyInactive ? "Inactive" : "Active"}
          </button>
          <button
            type="button"
            onClick={() => setEditing(true)}
            aria-label="Edit prize"
            className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-md border border-border text-muted-foreground hover:bg-card/80"
          >
            <Pencil className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            onClick={() => setConfirmDelete(true)}
            disabled={deletePrize.isPending}
            aria-label="Delete prize"
            className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-md border border-border text-muted-foreground hover:bg-card/80 disabled:opacity-50"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
      <AlertDialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete {prize.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              Permanently removes the prize from this crate. Past wins keep
              their name and image from the snapshot taken at spin time.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deletePrize.isPending}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={deletePrize.isPending}
              className="bg-rose-500 text-white hover:bg-rose-500/90"
            >
              {deletePrize.isPending ? "Deleting…" : "Delete prize"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </li>
  );
}

function PrizeEditForm({
  prize,
  tiers,
  onClose,
}: {
  readonly prize: LootboxPrize;
  readonly tiers: readonly LootboxTier[];
  readonly onClose: () => void;
}) {
  const [tierId, setTierId] = useState(prize.tierId);
  const [name, setName] = useState(prize.name);
  const [description, setDescription] = useState(prize.description ?? "");
  const [quantityInput, setQuantityInput] = useState(
    prize.quantity === null ? "" : String(prize.quantity)
  );
  const updatePrize = useUpdatePrizeMutation();
  const imageUpload = useImageUpload(prize.imageUrl);

  const save = async () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    const quantity = parseQuantityInput(quantityInput);
    if (quantity === "invalid") {
      toast({ title: "Quantity must be a non-negative whole number." });
      return;
    }
    try {
      const imageUrl = await imageUpload.upload();
      await updatePrize.mutateAsync({
        id: prize.id,
        body: {
          tierId,
          name: trimmed,
          description: description.trim() ? description.trim() : null,
          imageUrl: imageUrl ?? null,
          active: prize.active,
          quantity,
        },
      });
      toast({ title: `Updated ${trimmed}.`, variant: "success" });
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update prize.";
      toast({ title: "Couldn't update prize", description: message });
    }
  };

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1.5">
          <Label>Tier</Label>
          <Select value={tierId} onValueChange={setTierId}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {tiers.map((t) => (
                <SelectItem key={t.id} value={t.id}>
                  {t.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label>Name</Label>
          <Input value={name} onChange={(e) => setName(e.target.value)} />
        </div>
      </div>
      <div className="space-y-1.5">
        <Label>Description</Label>
        <Input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Stock (blank = unlimited; 0 = sold out)</Label>
        <Input
          type="number"
          inputMode="numeric"
          min={0}
          step={1}
          value={quantityInput}
          onChange={(e) => setQuantityInput(e.target.value)}
          placeholder="Unlimited"
        />
      </div>
      <div className="space-y-1.5">
        <Label>Image</Label>
        <ImageUpload
          displayUrl={imageUpload.displayUrl}
          isUploading={imageUpload.isUploading}
          error={imageUpload.error}
          hasNewFile={imageUpload.hasNewFile}
          onFileSelect={imageUpload.selectFile}
          onClear={imageUpload.clear}
        />
      </div>
      <div className="flex items-center justify-end gap-2">
        <Button variant="ghost" size="sm" onClick={onClose}>
          Cancel
        </Button>
        <Button
          size="sm"
          onClick={save}
          disabled={!name.trim() || updatePrize.isPending || imageUpload.isUploading}
        >
          {updatePrize.isPending || imageUpload.isUploading ? "Saving…" : "Save"}
        </Button>
      </div>
    </div>
  );
}

function NewPrizeSheet({
  tiers,
  onClose,
}: {
  readonly tiers: readonly LootboxTier[];
  readonly onClose: () => void;
}) {
  const [selectedTierId, setSelectedTierId] = useState("");
  const tierId = selectedTierId || tiers[0]?.id || "";
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [quantityInput, setQuantityInput] = useState("");
  const createPrize = useCreatePrizeMutation();
  const imageUpload = useImageUpload();

  const submit = async () => {
    if (!tierId || !name.trim()) return;
    const quantity = parseQuantityInput(quantityInput);
    if (quantity === "invalid") {
      toast({ title: "Quantity must be a non-negative whole number." });
      return;
    }
    try {
      const imageUrl = await imageUpload.upload();
      await createPrize.mutateAsync({
        tierId,
        name: name.trim(),
        description: description.trim() || undefined,
        imageUrl: imageUrl ?? undefined,
        quantity: quantity ?? undefined,
      });
      toast({ title: "Prize added.", variant: "success" });
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to add prize.";
      toast({ title: "Couldn't add prize", description: message });
    }
  };

  return (
    <div className="rounded-xl border border-brand-primary/30 bg-brand-primary/[0.04] p-3.5">
      <div className="mb-2.5 font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
        Add prize
      </div>
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label>Tier</Label>
            <Select value={tierId} onValueChange={setSelectedTierId}>
              <SelectTrigger>
                <SelectValue placeholder="Tier" />
              </SelectTrigger>
              <SelectContent>
                {tiers.map((t) => (
                  <SelectItem key={t.id} value={t.id}>
                    {t.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>Name</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label>Description (optional)</Label>
          <Input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label>Stock (blank = unlimited)</Label>
          <Input
            type="number"
            inputMode="numeric"
            min={0}
            step={1}
            value={quantityInput}
            onChange={(e) => setQuantityInput(e.target.value)}
            placeholder="Unlimited"
          />
        </div>
        <div className="space-y-1.5">
          <Label>Image (optional)</Label>
          <ImageUpload
            displayUrl={imageUpload.displayUrl}
            isUploading={imageUpload.isUploading}
            error={imageUpload.error}
            hasNewFile={imageUpload.hasNewFile}
            onFileSelect={imageUpload.selectFile}
            onClear={imageUpload.clear}
          />
        </div>
        <div className="flex items-center justify-end gap-2">
          <Button variant="ghost" size="sm" onClick={onClose}>
            Cancel
          </Button>
          <Button
            size="sm"
            onClick={submit}
            disabled={!name.trim() || !tierId || createPrize.isPending}
          >
            {createPrize.isPending ? "Adding…" : "Add prize"}
          </Button>
        </div>
      </div>
    </div>
  );
}

/**
 * Form input parser: blank string -> null (unlimited / no change), valid non-negative
 * integer -> number, anything else -> "invalid" sentinel for callers to toast on.
 */
function parseQuantityInput(raw: string): number | null | "invalid" {
  const trimmed = raw.trim();
  if (trimmed === "") return null;
  if (!/^\d+$/.test(trimmed)) return "invalid";
  const n = Number(trimmed);
  if (!Number.isFinite(n) || n < 0) return "invalid";
  return n;
}

function QuantityBadge({ quantity }: { readonly quantity: number | null }) {
  if (quantity === null) return null;
  if (quantity === 0) {
    return (
      <span className="inline-flex items-center rounded-full bg-rose-500/10 px-2.5 py-1 font-mono text-[10.5px] uppercase tracking-[0.06em] text-rose-500">
        WON out
      </span>
    );
  }
  return (
    <span className="inline-flex items-center rounded-full bg-amber-500/10 px-2.5 py-1 font-mono text-[10.5px] uppercase tracking-[0.06em] text-amber-500">
      {quantity} left
    </span>
  );
}

function pickContrastText(bg: string | null): string {
  if (!bg) return "#0a0a0c";
  const hex = bg.replace("#", "");
  if (hex.length !== 6) return "#fff";
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.6 ? "#0a0a0c" : "#fff";
}
