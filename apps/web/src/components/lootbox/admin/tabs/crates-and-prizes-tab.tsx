"use client";

import { useMemo, useState } from "react";
import { format } from "date-fns";
import { Calendar as CalendarIcon, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Calendar } from "@/components/ui/calendar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
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
import { toast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import {
  useLootboxAdminCatalog,
  useLootboxAdminCrates,
} from "@/hooks/queries/use-lootbox";
import {
  useBulkUpdateTierProbabilitiesMutation,
  useCreateCrateMutation,
  useDeleteCrateMutation,
  useDeleteTierMutation,
  useUpdateCrateMutation,
} from "@/hooks/mutations/use-lootbox-mutations";
import { CrateListRail } from "@/components/lootbox/admin/crates/crate-list-rail";
import {
  TierWeightsEditor,
  tierWeightSum,
} from "@/components/lootbox/admin/crates/tier-weights-editor";
import { NewTierSheet } from "@/components/lootbox/admin/crates/new-tier-sheet";
import { CratePrizesSection } from "@/components/lootbox/admin/crates/crate-prizes-section";
import { TimePickerButton } from "@/components/lootbox/admin/crates/time-picker-button";
import type { Lootbox, LootboxAdmin, LootboxTier } from "@/types/lootbox";

function toEndsState(iso: string | null): { date: Date | undefined; time: string } {
  if (!iso) return { date: undefined, time: "23:59" };
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return { date: undefined, time: "23:59" };
  const pad = (n: number) => String(n).padStart(2, "0");
  return { date: d, time: `${pad(d.getHours())}:${pad(d.getMinutes())}` };
}

function combineDateAndTime(date: Date | undefined, time: string): string | null {
  if (!date) return null;
  const [hh, mm] = time.split(":").map((part) => Number.parseInt(part, 10));
  if (!Number.isFinite(hh) || !Number.isFinite(mm)) return null;
  const merged = new Date(date);
  merged.setHours(hh, mm, 0, 0);
  return merged.toISOString();
}

function formatEndsIn(iso: string | null): string | null {
  if (!iso) return null;
  const end = new Date(iso).getTime();
  const diff = end - Date.now();
  if (diff <= 0) return "Closed";
  const days = Math.floor(diff / (24 * 60 * 60 * 1000));
  const hours = Math.floor((diff / (60 * 60 * 1000)) % 24);
  if (days > 0) return `Ends in ${days}d ${hours}h`;
  const minutes = Math.floor((diff / (60 * 1000)) % 60);
  return `Ends in ${hours}h ${minutes}m`;
}

export function CratesAndPrizesTab() {
  const cratesQuery = useLootboxAdminCrates();
  const catalogQuery = useLootboxAdminCatalog();

  const crates: readonly LootboxAdmin[] = useMemo(
    () => cratesQuery.data ?? [],
    [cratesQuery.data]
  );
  const catalog: readonly Lootbox[] = useMemo(
    () => catalogQuery.data ?? [],
    [catalogQuery.data]
  );

  const [pickedCrateId, setPickedCrateId] = useState<string | null>(null);

  // Active selection is derived during render: the user's pick if still valid,
  // otherwise the first crate. Avoids an effect-driven setState bounce.
  const activeCrateId =
    pickedCrateId && crates.some((c) => c.id === pickedCrateId)
      ? pickedCrateId
      : crates[0]?.id ?? null;

  const activeAdmin = crates.find((c) => c.id === activeCrateId) ?? null;
  const activeCatalog = catalog.find((c) => c.id === activeCrateId) ?? null;
  // Show every tier (and every prize within it), including ones the admin has parked
  // at 0% / `active=false`. Inactive rows render faded with an "Inactive" badge so
  // they can be reactivated (give weight + active prize) or hard-deleted from the
  // same view — hiding them used to be safe because they couldn't be removed, but
  // V45's FK SET NULL/CASCADE makes hard-delete the explicit purge path.
  const allTiers: readonly LootboxTier[] = useMemo(() => {
    const tiers = activeCatalog?.tiers ?? [];
    // Active first, inactive at the bottom; preserve sort_order within each group.
    return [...tiers].sort((a, b) => {
      if (a.active !== b.active) return a.active ? -1 : 1;
      return 0;
    });
  }, [activeCatalog]);

  const createMut = useCreateCrateMutation();
  const handleNew = async () => {
    try {
      const created = await createMut.mutateAsync({
        name: "New crate",
        cost: 1,
        active: false,
      });
      setPickedCrateId(created.id);
      toast({ title: "Draft crate created.", variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to create crate.";
      toast({ title: "Couldn't create crate", description: message });
    }
  };

  return (
    <div className="grid h-full grid-cols-1 grid-rows-[auto_1fr] overflow-hidden md:grid-cols-[220px_1fr] md:grid-rows-1">
      <CrateListRail
        crates={crates}
        activeCrateId={activeCrateId}
        onSelect={setPickedCrateId}
        onNew={handleNew}
        isLoading={cratesQuery.isLoading}
        isCreating={createMut.isPending}
      />
      <div className="flex min-h-0 flex-col">
        {cratesQuery.isLoading || catalogQuery.isLoading ? (
          <div className="space-y-2 p-4 sm:p-6">
            <Skeleton className="h-10 w-1/2" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-32 w-full" />
          </div>
        ) : activeAdmin ? (
          <CrateConfigForm
            key={activeAdmin.id}
            admin={activeAdmin}
            tiers={allTiers}
          />
        ) : (
          <p className="p-4 text-sm text-muted-foreground sm:p-6">
            Select a crate from the left, or create a new one.
          </p>
        )}
      </div>
    </div>
  );
}

interface CrateConfigFormProps {
  readonly admin: LootboxAdmin;
  readonly tiers: readonly LootboxTier[];
}

function CrateConfigForm({ admin, tiers }: CrateConfigFormProps) {
  const initialEnds = toEndsState(admin.endsAt);
  const [name, setName] = useState(admin.name);
  const [description, setDescription] = useState(admin.description ?? "");
  const [cost, setCost] = useState(String(admin.cost));
  const [active, setActive] = useState(admin.active);
  const [limited, setLimited] = useState(!!admin.endsAt);
  const [endsDate, setEndsDate] = useState<Date | undefined>(initialEnds.date);
  const [endsTime, setEndsTime] = useState<string>(initialEnds.time);
  const [weightEdits, setWeightEdits] = useState<Record<string, string>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [isAddingTier, setIsAddingTier] = useState(false);
  const [tierPendingDelete, setTierPendingDelete] =
    useState<LootboxTier | null>(null);

  const weightSum = tierWeightSum(tiers, weightEdits);
  const weightSumOk = Math.abs(weightSum - 100) < 0.05;

  const updateMut = useUpdateCrateMutation();
  const deleteMut = useDeleteCrateMutation();
  const bulkUpdateWeights = useBulkUpdateTierProbabilitiesMutation();
  const deleteTierMut = useDeleteTierMutation();

  const deleteDisabledTierIds = useMemo(() => {
    const set = new Set<string>();
    if (deleteTierMut.isPending && tierPendingDelete) {
      set.add(tierPendingDelete.id);
    }
    return set;
  }, [deleteTierMut.isPending, tierPendingDelete]);

  const handleDeleteTier = async () => {
    if (!tierPendingDelete) return;
    const tier = tierPendingDelete;
    try {
      await deleteTierMut.mutateAsync({ id: tier.id });
      toast({ title: `Deleted tier "${tier.name}".`, variant: "success" });
      setTierPendingDelete(null);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to delete tier.";
      toast({ title: "Couldn't delete tier", description: message });
    }
  };

  const trimmedName = name.trim();
  const numericCost = Number.parseInt(cost, 10);
  const validCost = Number.isFinite(numericCost) && numericCost >= 0;
  const endsIso = limited ? combineDateAndTime(endsDate, endsTime) : null;
  const endsInLabel = endsIso ? formatEndsIn(endsIso) : null;

  const hasWeightEdits = Object.keys(weightEdits).length > 0;
  const dirty =
    trimmedName !== admin.name ||
    (description.trim() || null) !== (admin.description ?? null) ||
    numericCost !== admin.cost ||
    active !== admin.active ||
    (limited ? endsIso : null) !== admin.endsAt ||
    hasWeightEdits;

  const canSave =
    !!trimmedName &&
    validCost &&
    (hasWeightEdits ? weightSumOk : true) &&
    !updateMut.isPending &&
    !bulkUpdateWeights.isPending &&
    dirty;

  const canDelete = admin.prizeCount === 0 || !admin.active;

  const handleSave = async () => {
    if (!canSave) return;
    try {
      await updateMut.mutateAsync({
        id: admin.id,
        body: {
          name: trimmedName,
          description: description.trim() ? description.trim() : null,
          imageUrl: admin.imageUrl,
          cost: numericCost,
          startsAt: admin.startsAt,
          endsAt: endsIso,
          active,
          siteId: admin.siteId,
          sortOrder: admin.sortOrder,
        },
      });
      if (hasWeightEdits) {
        const payload = tiers
          .filter((t) => t.id in weightEdits)
          .map((t) => ({
            id: t.id,
            probabilityPct: Number.parseFloat(weightEdits[t.id]),
          }))
          .filter((c) => Number.isFinite(c.probabilityPct));
        if (payload.length > 0) {
          await bulkUpdateWeights.mutateAsync({
            lootboxId: admin.id,
            tiers: payload,
          });
        }
      }
      setWeightEdits({});
      toast({ title: "Crate saved.", variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save crate.";
      toast({ title: "Couldn't save crate", description: message });
    }
  };

  const handleDelete = async () => {
    try {
      await deleteMut.mutateAsync({ id: admin.id });
      toast({ title: `Deleted ${admin.name}.`, variant: "success" });
      setConfirmDelete(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete crate.";
      toast({ title: "Couldn't delete crate", description: message });
    }
  };

  return (
    <>
      <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6">
        <div className="mx-auto flex max-w-[600px] flex-col gap-[22px]">
          <Section label="Identity">
            <div className="grid grid-cols-[1fr_140px] gap-3">
              <FieldStack label="Name">
                <Input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={60}
                />
              </FieldStack>
              <FieldStack label="Cost">
                <div className="relative">
                  <Input
                    type="number"
                    min="0"
                    max="999"
                    value={cost}
                    onChange={(e) => setCost(e.target.value)}
                    className="pr-12 font-mono tabular-nums"
                  />
                  <span className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 font-mono text-[11px] text-muted-foreground">
                    coin
                  </span>
                </div>
              </FieldStack>
            </div>
          </Section>

          <FieldStack label="Description">
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={80}
              placeholder="Optional"
            />
          </FieldStack>

          <Section label="Status">
            <label className="flex items-center gap-2.5 text-sm text-foreground">
              <input
                type="checkbox"
                checked={active}
                onChange={(e) => setActive(e.target.checked)}
                className="h-4 w-4 cursor-pointer rounded border-border bg-card accent-brand-primary"
              />
              <span>Active (visible to players)</span>
            </label>
          </Section>

          <Section label="Limited-time">
            <label className="flex items-center gap-2.5 text-sm text-foreground">
              <input
                type="checkbox"
                checked={limited}
                onChange={(e) => setLimited(e.target.checked)}
                className="h-4 w-4 cursor-pointer rounded border-border bg-card accent-brand-primary"
              />
              <span>Limited-time crate</span>
            </label>
            {limited ? (
              <div className="mt-3 space-y-3">
                <div className="grid grid-cols-[1fr_140px] items-end gap-3">
                  <FieldStack label="Ends on">
                    <Popover>
                      <PopoverTrigger asChild>
                        <Button
                          variant="outline"
                          className={cn(
                            "w-full justify-start text-left font-normal dark:bg-input dark:border-[#41413d]",
                            !endsDate && "text-muted-foreground"
                          )}
                        >
                          {endsDate ? (
                            format(endsDate, "MM/dd/yyyy")
                          ) : (
                            <span>mm/dd/yyyy</span>
                          )}
                          <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                        </Button>
                      </PopoverTrigger>
                      <PopoverContent className="w-auto p-0" align="start">
                        <Calendar
                          mode="single"
                          selected={endsDate}
                          onSelect={(date) => setEndsDate(date ?? undefined)}
                        />
                      </PopoverContent>
                    </Popover>
                  </FieldStack>
                  <FieldStack label="Ends at (time)">
                    <TimePickerButton
                      value={endsTime}
                      onChange={setEndsTime}
                    />
                  </FieldStack>
                </div>
                {endsInLabel ? (
                  <p className="font-mono text-[11px] text-muted-foreground">
                    {endsInLabel}
                  </p>
                ) : null}
              </div>
            ) : null}
          </Section>

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
                Tiers · <span className="tabular-nums">{tiers.length}</span>
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsAddingTier(true)}
                disabled={isAddingTier}
              >
                <Plus className="h-4 w-4" />
                Add tier
              </Button>
            </div>

            {isAddingTier ? (
              <NewTierSheet
                lootboxId={admin.id}
                existingTiers={tiers}
                onClose={() => setIsAddingTier(false)}
              />
            ) : null}

            {tiers.length > 0 ? (
              <TierWeightsEditor
                tiers={tiers}
                edits={weightEdits}
                onEditsChange={setWeightEdits}
                onDeleteTier={setTierPendingDelete}
                deleteDisabledFor={deleteDisabledTierIds}
              />
            ) : (
              <p className="rounded-xl border border-dashed border-border p-4 text-[13px] text-muted-foreground">
                No tiers yet. Click <span className="font-medium">Add tier</span> to create one.
              </p>
            )}
          </div>

          <CratePrizesSection tiers={tiers} />
        </div>
      </div>

      <div className="flex flex-none items-center justify-between border-t border-border bg-background/95 px-4 py-3 sm:px-6 sm:py-4">
        <div>
          {canDelete ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setConfirmDelete(true)}
              disabled={deleteMut.isPending}
              className="text-rose-400 hover:bg-rose-500/10 hover:text-rose-300"
            >
              <Trash2 className="h-4 w-4" />
              Delete crate
            </Button>
          ) : (
            <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
              Cannot delete · has prizes
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Button onClick={handleSave} disabled={!canSave}>
            {updateMut.isPending || bulkUpdateWeights.isPending
              ? "Saving…"
              : "Save changes"}
          </Button>
        </div>
      </div>

      <AlertDialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete {admin.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              This permanently removes the crate and all of its tiers and prizes.
              Crates with recorded plays can&apos;t be deleted — deactivate them
              instead.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMut.isPending}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={deleteMut.isPending}
              className="bg-rose-500 text-white hover:bg-rose-500/90"
            >
              {deleteMut.isPending ? "Deleting…" : "Delete crate"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={tierPendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setTierPendingDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              Delete tier {tierPendingDelete?.name}?
            </AlertDialogTitle>
            <AlertDialogDescription>
              Permanently removes the tier and all of its prizes. Past wins keep
              their name, image, and tier label from the snapshot taken at spin
              time. Remaining tiers are rebalanced to sum to 100%.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteTierMut.isPending}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDeleteTier}
              disabled={deleteTierMut.isPending}
              className="bg-rose-500 text-white hover:bg-rose-500/90"
            >
              {deleteTierMut.isPending ? "Deleting…" : "Delete tier"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

function Section({
  label,
  children,
}: {
  readonly label: string;
  readonly children: React.ReactNode;
}) {
  return (
    <div className="space-y-2.5">
      <div className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
        {label}
      </div>
      {children}
    </div>
  );
}

function FieldStack({
  label,
  children,
}: {
  readonly label: string;
  readonly children: React.ReactNode;
}) {
  return (
    <div className="space-y-2">
      <Label className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-muted-foreground">
        {label}
      </Label>
      {children}
    </div>
  );
}
