"use client";

import { useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
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
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { toast } from "@/hooks/use-toast";
import { useLootboxAdminCrates } from "@/hooks/queries/use-lootbox";
import {
  useCreateCrateMutation,
  useDeleteCrateMutation,
  useUpdateCrateMutation,
} from "@/hooks/mutations/use-lootbox-mutations";
import type { LootboxAdmin, UpsertLootboxRequest } from "@/types/lootbox";

/**
 * Admin page for managing crates ("Lootbox" rows). Lists all crates, lets admins
 * create / edit / delete with date pickers for the optional active window. Date input
 * values are interpreted in the user's browser timezone (Postgres TIMESTAMPTZ stores
 * the equivalent instant), matching the rest of the dashboard's datetime UX.
 */
export default function LootboxAdminCratesPage() {
  const cratesQuery = useLootboxAdminCrates();
  const [editTarget, setEditTarget] = useState<LootboxAdmin | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<LootboxAdmin | null>(null);

  const crates = cratesQuery.data ?? [];

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <SidebarTrigger />
          <h1 className="text-2xl font-semibold tracking-tight">Crates</h1>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4" />
          New crate
        </Button>
      </div>

      {cratesQuery.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : crates.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No crates yet. Create one to get started.
        </p>
      ) : (
        <div className="overflow-x-auto rounded-md border">
          <table className="min-w-full divide-y divide-border text-sm">
            <thead className="bg-muted/30">
              <tr className="text-left">
                <Th>Name</Th>
                <Th>Cost</Th>
                <Th>Window</Th>
                <Th>Tiers</Th>
                <Th>Prizes</Th>
                <Th>Status</Th>
                <Th className="text-right">Actions</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {crates.map((c) => (
                <tr key={c.id} className="hover:bg-muted/20">
                  <Td>
                    <div className="font-medium">{c.name}</div>
                    {c.description ? (
                      <div className="text-xs text-muted-foreground truncate max-w-xs">
                        {c.description}
                      </div>
                    ) : null}
                  </Td>
                  <Td>
                    <span className="font-mono tabular-nums">{c.cost}</span>
                  </Td>
                  <Td>
                    <span className="font-mono text-xs text-muted-foreground">
                      {formatWindow(c.startsAt, c.endsAt)}
                    </span>
                  </Td>
                  <Td className="tabular-nums">{c.tierCount}</Td>
                  <Td className="tabular-nums">{c.prizeCount}</Td>
                  <Td>
                    <Badge variant={c.active ? "default" : "secondary"}>
                      {c.active ? "Active" : "Inactive"}
                    </Badge>
                  </Td>
                  <Td className="text-right">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setEditTarget(c)}
                      aria-label="Edit crate"
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setDeleteTarget(c)}
                      aria-label="Delete crate"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <CrateFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        mode="create"
        initial={null}
      />
      <CrateFormDialog
        open={!!editTarget}
        onOpenChange={(v) => !v && setEditTarget(null)}
        mode="edit"
        initial={editTarget}
      />
      <DeleteConfirmDialog
        target={deleteTarget}
        onOpenChange={(v) => !v && setDeleteTarget(null)}
      />
    </div>
  );
}

function Th({
  children,
  className,
}: {
  readonly children: React.ReactNode;
  readonly className?: string;
}) {
  return (
    <th className={`px-4 py-2 font-medium text-muted-foreground ${className ?? ""}`}>
      {children}
    </th>
  );
}

function Td({
  children,
  className,
}: {
  readonly children: React.ReactNode;
  readonly className?: string;
}) {
  return <td className={`px-4 py-3 align-top ${className ?? ""}`}>{children}</td>;
}

function formatWindow(startsAt: string | null, endsAt: string | null): string {
  if (!startsAt && !endsAt) return "Always open";
  const fmt = (s: string) => new Date(s).toLocaleString();
  if (startsAt && endsAt) return `${fmt(startsAt)} → ${fmt(endsAt)}`;
  if (startsAt) return `From ${fmt(startsAt)}`;
  return `Until ${fmt(endsAt!)}`;
}

// ---------- Create / edit dialog ----------

interface CrateFormDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly mode: "create" | "edit";
  readonly initial: LootboxAdmin | null;
}

function CrateFormDialog({ open, onOpenChange, mode, initial }: CrateFormDialogProps) {
  const createMut = useCreateCrateMutation();
  const updateMut = useUpdateCrateMutation();
  const submitting = createMut.isPending || updateMut.isPending;

  const submit = async (body: UpsertLootboxRequest) => {
    try {
      if (mode === "create") {
        await createMut.mutateAsync(body);
        toast({ title: "Crate created.", variant: "success" });
      } else if (initial) {
        await updateMut.mutateAsync({ id: initial.id, body });
        toast({ title: "Crate updated.", variant: "success" });
      }
      onOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save crate.";
      toast({ title: "Couldn't save crate", description: message });
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "New crate" : "Edit crate"}</DialogTitle>
        </DialogHeader>
        {open ? (
          <CrateForm
            key={initial?.id ?? "new"}
            initial={initial}
            submitting={submitting}
            onSubmit={submit}
            onCancel={() => onOpenChange(false)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function CrateForm({
  initial,
  submitting,
  onSubmit,
  onCancel,
}: {
  readonly initial: LootboxAdmin | null;
  readonly submitting: boolean;
  readonly onSubmit: (body: UpsertLootboxRequest) => void | Promise<void>;
  readonly onCancel: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [imageUrl, setImageUrl] = useState(initial?.imageUrl ?? "");
  const [cost, setCost] = useState<string>(String(initial?.cost ?? 1));
  const [startsAt, setStartsAt] = useState(toLocalInput(initial?.startsAt ?? null));
  const [endsAt, setEndsAt] = useState(toLocalInput(initial?.endsAt ?? null));
  const [active, setActive] = useState(initial?.active ?? true);
  const [sortOrder, setSortOrder] = useState<string>(String(initial?.sortOrder ?? 0));

  const trimmedName = name.trim();
  const numericCost = Number.parseInt(cost, 10);
  const validCost = Number.isFinite(numericCost) && numericCost >= 0;
  const startsIso = startsAt ? new Date(startsAt).toISOString() : null;
  const endsIso = endsAt ? new Date(endsAt).toISOString() : null;
  const dateOrderOk =
    !startsIso || !endsIso || new Date(endsIso).getTime() > new Date(startsIso).getTime();

  const canSubmit = !!trimmedName && validCost && dateOrderOk && !submitting;

  const handleSubmit = () => {
    void onSubmit({
      name: trimmedName,
      description: description.trim() || null,
      imageUrl: imageUrl.trim() || null,
      cost: numericCost,
      startsAt: startsIso,
      endsAt: endsIso,
      active,
      sortOrder: Number.parseInt(sortOrder, 10) || 0,
    });
  };

  return (
    <div className="space-y-3">
      <Field label="Name">
        <Input value={name} onChange={(e) => setName(e.target.value)} />
      </Field>
      <Field label="Description (optional)">
        <Input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>
      <Field label="Image URL (optional)">
        <Input value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Cost (coins)">
          <Input
            type="number"
            min="0"
            value={cost}
            onChange={(e) => setCost(e.target.value)}
          />
        </Field>
        <Field label="Sort order">
          <Input
            type="number"
            value={sortOrder}
            onChange={(e) => setSortOrder(e.target.value)}
          />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Opens (optional)">
          <Input
            type="datetime-local"
            value={startsAt}
            onChange={(e) => setStartsAt(e.target.value)}
          />
        </Field>
        <Field label="Closes (optional)">
          <Input
            type="datetime-local"
            value={endsAt}
            onChange={(e) => setEndsAt(e.target.value)}
          />
          {!dateOrderOk ? (
            <p className="text-xs text-rose-600">Closes must be after Opens.</p>
          ) : null}
        </Field>
      </div>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={active}
          onChange={(e) => setActive(e.target.checked)}
        />
        <span>Active (visible to players)</span>
      </label>

      <DialogFooter>
        <Button variant="ghost" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
        <Button onClick={handleSubmit} disabled={!canSubmit}>
          {submitting ? "Saving…" : initial ? "Save changes" : "Create crate"}
        </Button>
      </DialogFooter>
    </div>
  );
}

function Field({
  label,
  children,
}: {
  readonly label: string;
  readonly children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      {children}
    </div>
  );
}

/** Convert ISO 8601 (TIMESTAMPTZ from API) into the `YYYY-MM-DDTHH:MM` form datetime-local expects. */
function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours()
  )}:${pad(d.getMinutes())}`;
}

// ---------- Delete confirm ----------

function DeleteConfirmDialog({
  target,
  onOpenChange,
}: {
  readonly target: LootboxAdmin | null;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const deleteMut = useDeleteCrateMutation();

  const confirm = async () => {
    if (!target) return;
    try {
      await deleteMut.mutateAsync({ id: target.id });
      toast({ title: `Deleted ${target.name}.`, variant: "success" });
      onOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete crate.";
      toast({ title: "Couldn't delete crate", description: message });
    }
  };

  return (
    <Dialog open={!!target} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete crate?</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          {target ? (
            <>
              This will permanently remove <span className="font-medium">{target.name}</span> and
              all of its tiers + prizes. Crates with recorded plays can&apos;t be deleted — deactivate
              them instead.
            </>
          ) : null}
        </p>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={confirm}
            disabled={deleteMut.isPending}
          >
            {deleteMut.isPending ? "Deleting…" : "Delete crate"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
