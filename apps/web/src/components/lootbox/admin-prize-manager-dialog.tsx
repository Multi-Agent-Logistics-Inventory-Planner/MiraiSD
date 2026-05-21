"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ImageUpload } from "@/components/ui/image-upload";
import { useImageUpload } from "@/hooks/use-image-upload";
import { toast } from "@/hooks/use-toast";
import { useLootboxAdminCatalog } from "@/hooks/queries/use-lootbox";
import {
  useBulkUpdateTierProbabilitiesMutation,
  useCreatePrizeMutation,
  useDeletePrizeMutation,
  useUpdatePrizeMutation,
  useUpdateTierMutation,
} from "@/hooks/mutations/use-lootbox-mutations";
import type { LootboxPrize, LootboxTier } from "@/types/lootbox";
import { Trash2, Plus, Pencil, X, Check } from "lucide-react";

interface AdminPrizeManagerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function TierProbabilityEditor({
  lootboxId,
  tiers,
}: {
  lootboxId: string;
  tiers: LootboxTier[];
}) {
  const bulkUpdate = useBulkUpdateTierProbabilitiesMutation();
  // Track only user-edited values as an overlay. Unedited tiers show their canonical
  // probability_pct from the server. Reset on save by clearing all overrides.
  const [edits, setEdits] = useState<Record<string, string>>({});

  const valueFor = (t: LootboxTier) =>
    edits[t.id] ?? Number(t.probabilityPct).toFixed(2);

  const sum = tiers.reduce(
    (acc, t) => acc + (Number.parseFloat(valueFor(t)) || 0),
    0
  );
  const sumOk = Math.abs(sum - 100) < 0.05;
  const hasEdits = Object.keys(edits).length > 0;
  const canSave = hasEdits && sumOk && !bulkUpdate.isPending;

  const saveAll = async () => {
    const payload = tiers
      .filter((t) => t.id in edits)
      .map((t) => ({
        id: t.id,
        probabilityPct: Number.parseFloat(valueFor(t)),
      }))
      .filter((c) => Number.isFinite(c.probabilityPct));
    if (payload.length === 0) return;
    try {
      await bulkUpdate.mutateAsync({ lootboxId, tiers: payload });
      setEdits({});
      toast({ title: `Updated ${payload.length} tier(s).`, variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update tiers.";
      toast({ title: "Couldn't update tiers", description: message });
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <h4 className="font-medium">Tiers</h4>
        <div className="flex items-center gap-3">
          <span className={sumOk ? "text-xs text-muted-foreground" : "text-xs text-rose-600"}>
            Total {sum.toFixed(2)}% {sumOk ? "" : "(must equal 100)"}
          </span>
          <Button size="sm" onClick={saveAll} disabled={!canSave}>
            {bulkUpdate.isPending ? "Saving…" : "Save"}
          </Button>
        </div>
      </div>
      <ul className="space-y-2">
        {tiers.map((t) => (
          <TierRow
            key={t.id}
            tier={t}
            value={valueFor(t)}
            onProbabilityChange={(v) =>
              setEdits((prev) => ({ ...prev, [t.id]: v }))
            }
          />
        ))}
      </ul>
    </div>
  );
}

function TierRow({
  tier,
  value,
  onProbabilityChange,
}: {
  tier: LootboxTier;
  value: string;
  onProbabilityChange: (v: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(tier.name);
  const [color, setColor] = useState(tier.displayColor ?? "#888888");
  const updateTier = useUpdateTierMutation();

  const cancel = () => {
    setName(tier.name);
    setColor(tier.displayColor ?? "#888888");
    setEditing(false);
  };

  const save = async () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    try {
      await updateTier.mutateAsync({
        id: tier.id,
        body: {
          name: trimmed,
          // Backend update tolerates null fields; we still pass current probability so
          // the request stays valid against the create-shaped UpsertTierRequest type.
          probabilityPct: Number(tier.probabilityPct),
          displayColor: color,
        },
      });
      toast({ title: `Updated ${trimmed}.`, variant: "success" });
      setEditing(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update tier.";
      toast({ title: "Couldn't update tier", description: message });
    }
  };

  if (editing) {
    return (
      <li className="flex items-center gap-2 rounded-md border p-2">
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Tier name"
          className="w-32"
        />
        <input
          type="color"
          value={color}
          onChange={(e) => setColor(e.target.value)}
          className="h-9 w-9 cursor-pointer rounded border bg-transparent"
          aria-label="Tier color"
        />
        <span className="ml-auto flex items-center gap-1">
          <Button
            size="sm"
            variant="ghost"
            onClick={save}
            disabled={!name.trim() || updateTier.isPending}
            aria-label="Save tier"
          >
            <Check className="h-4 w-4" />
          </Button>
          <Button size="sm" variant="ghost" onClick={cancel} aria-label="Cancel">
            <X className="h-4 w-4" />
          </Button>
        </span>
      </li>
    );
  }

  return (
    <li className="flex items-center gap-2">
      <Badge
        variant="secondary"
        style={
          tier.displayColor
            ? { backgroundColor: tier.displayColor, color: "white" }
            : undefined
        }
        className="min-w-20 justify-center"
      >
        {tier.name}
      </Badge>
      <Input
        type="number"
        step="0.01"
        min="0"
        max="100"
        value={value}
        onChange={(e) => onProbabilityChange(e.target.value)}
        className="w-24"
      />
      <span className="text-sm text-muted-foreground">%</span>
      <Button
        size="sm"
        variant="ghost"
        onClick={() => setEditing(true)}
        aria-label="Edit tier"
      >
        <Pencil className="h-4 w-4" />
      </Button>
      <span className="text-xs text-muted-foreground ml-auto">
        {tier.prizes.length} prize{tier.prizes.length === 1 ? "" : "s"}
      </span>
    </li>
  );
}

function PrizeRow({
  prize,
  tiers,
}: {
  prize: LootboxPrize;
  tiers: LootboxTier[];
}) {
  const updatePrize = useUpdatePrizeMutation();
  const deletePrize = useDeletePrizeMutation();
  const [editing, setEditing] = useState(false);

  const handleToggle = async () => {
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

  const handleDelete = async () => {
    try {
      await deletePrize.mutateAsync({ id: prize.id });
      toast({ title: `Deactivated ${prize.name}.`, variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete prize.";
      toast({ title: "Couldn't delete prize", description: message });
    }
  };

  if (editing) {
    return (
      <PrizeEditForm
        prize={prize}
        tiers={tiers}
        onClose={() => setEditing(false)}
      />
    );
  }

  return (
    <li className="py-2 flex items-center gap-3">
      <div className="min-w-0 flex-1">
        <div className="font-medium truncate">{prize.name}</div>
        {prize.description && (
          <div className="text-xs text-muted-foreground truncate">{prize.description}</div>
        )}
      </div>
      <Badge variant={prize.active ? "default" : "secondary"}>
        {prize.active ? "Active" : "Inactive"}
      </Badge>
      <Button size="sm" variant="outline" onClick={handleToggle}>
        {prize.active ? "Deactivate" : "Activate"}
      </Button>
      <Button
        size="sm"
        variant="ghost"
        onClick={() => setEditing(true)}
        aria-label="Edit prize"
      >
        <Pencil className="h-4 w-4" />
      </Button>
      <Button
        size="sm"
        variant="ghost"
        onClick={handleDelete}
        disabled={deletePrize.isPending}
        aria-label="Delete prize"
      >
        <Trash2 className="h-4 w-4" />
      </Button>
    </li>
  );
}

function PrizeEditForm({
  prize,
  tiers,
  onClose,
}: {
  prize: LootboxPrize;
  tiers: LootboxTier[];
  onClose: () => void;
}) {
  const [tierId, setTierId] = useState(prize.tierId);
  const [name, setName] = useState(prize.name);
  const [description, setDescription] = useState(prize.description ?? "");
  const updatePrize = useUpdatePrizeMutation();
  const imageUpload = useImageUpload(prize.imageUrl);

  const save = async () => {
    const trimmed = name.trim();
    if (!trimmed) return;
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
    <li className="py-3 space-y-3 border-t first:border-t-0">
      <div className="grid grid-cols-2 gap-2">
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
    </li>
  );
}

function NewPrizeForm({ tiers }: { tiers: LootboxTier[] }) {
  // Track the user's explicit selection separately so it doesn't get clobbered when
  // the tier list refreshes; fall back to the first tier when nothing's selected.
  const [selectedTierId, setSelectedTierId] = useState<string>("");
  const tierId = selectedTierId || tiers[0]?.id || "";
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const createPrize = useCreatePrizeMutation();
  const imageUpload = useImageUpload();

  const submit = async () => {
    if (!tierId || !name.trim()) return;
    try {
      const imageUrl = await imageUpload.upload();
      await createPrize.mutateAsync({
        tierId,
        name: name.trim(),
        description: description.trim() || undefined,
        imageUrl: imageUrl ?? undefined,
      });
      setName("");
      setDescription("");
      imageUpload.reset();
      toast({ title: "Prize added.", variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to add prize.";
      toast({ title: "Couldn't add prize", description: message });
    }
  };

  return (
    <div className="space-y-3 border-t pt-3">
      <h4 className="font-medium">Add a prize</h4>
      <div className="grid grid-cols-2 gap-2">
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
        <Input value={description} onChange={(e) => setDescription(e.target.value)} />
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
      <Button onClick={submit} disabled={!name.trim() || createPrize.isPending}>
        <Plus className="h-4 w-4" />
        Add prize
      </Button>
    </div>
  );
}

export function AdminPrizeManagerDialog({
  open,
  onOpenChange,
}: AdminPrizeManagerDialogProps) {
  const catalogQuery = useLootboxAdminCatalog();
  const crates = catalogQuery.data ?? [];
  const [selectedCrateId, setSelectedCrateId] = useState<string>("");
  const activeCrateId = selectedCrateId || crates[0]?.id || "";
  const activeCrate = crates.find((c) => c.id === activeCrateId);
  const tiers = activeCrate?.tiers ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Manage Lootbox Prizes</DialogTitle>
        </DialogHeader>
        {catalogQuery.isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : crates.length === 0 ? (
          <p className="text-sm text-muted-foreground">No crates configured yet.</p>
        ) : (
          <div className="space-y-6">
            <div className="space-y-1.5">
              <Label>Crate</Label>
              <Select value={activeCrateId} onValueChange={setSelectedCrateId}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {crates.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {activeCrate ? (
              <>
                <TierProbabilityEditor lootboxId={activeCrate.id} tiers={tiers} />
                <div className="space-y-2">
                  <h4 className="font-medium">Prizes</h4>
                  {tiers.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No tiers configured yet.</p>
                  ) : (
                    tiers.map((tier) => (
                      <div key={tier.id} className="border rounded-md p-3">
                        <div className="flex items-center justify-between mb-2">
                          <Badge
                            variant="secondary"
                            style={
                              tier.displayColor
                                ? { backgroundColor: tier.displayColor, color: "white" }
                                : undefined
                            }
                          >
                            {tier.name}
                          </Badge>
                          <span className="text-xs text-muted-foreground">
                            {Number(tier.probabilityPct).toFixed(2)}%
                          </span>
                        </div>
                        {tier.prizes.length === 0 ? (
                          <p className="text-xs text-muted-foreground">No prizes in this tier.</p>
                        ) : (
                          <ul className="divide-y">
                            {tier.prizes.map((p) => (
                              <PrizeRow key={p.id} prize={p} tiers={tiers} />
                            ))}
                          </ul>
                        )}
                      </div>
                    ))
                  )}
                </div>
                {tiers.length > 0 && <NewPrizeForm tiers={tiers} />}
              </>
            ) : null}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
