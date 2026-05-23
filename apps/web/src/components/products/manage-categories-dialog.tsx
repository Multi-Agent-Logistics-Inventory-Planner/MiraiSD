"use client";

import { useMemo, useState } from "react";
import {
  AlertTriangle,
  Check,
  ChevronRight,
  Loader2,
  Package,
  Pencil,
  Plus,
  Search,
  Trash2,
  X,
} from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useCategories } from "@/hooks/queries/use-categories";
import {
  useCreateCategoryMutation,
  useDeleteCategoryMutation,
  useUpdateCategoryMutation,
} from "@/hooks/mutations/use-category-mutations";
import type { Category } from "@/types/api";

interface ManageCategoriesDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const ACT_BTN =
  "inline-flex h-[26px] w-[26px] items-center justify-center rounded-[7px] text-muted-foreground/70 transition-colors hover:bg-muted hover:text-foreground disabled:pointer-events-none disabled:opacity-30";
const DANGER_BTN =
  "inline-flex h-[26px] w-[26px] items-center justify-center rounded-[7px] text-muted-foreground/70 transition-colors hover:bg-destructive/10 hover:text-destructive disabled:pointer-events-none disabled:opacity-30";
const OK_BTN =
  "inline-flex h-[26px] w-[26px] items-center justify-center rounded-[7px] bg-emerald-500/15 text-emerald-600 transition-colors hover:bg-emerald-500/25 dark:text-emerald-400 disabled:pointer-events-none disabled:opacity-50";

export function ManageCategoriesDialog({
  open,
  onOpenChange,
}: ManageCategoriesDialogProps) {
  const { toast } = useToast();
  const { data: categories, isLoading } = useCategories();
  const createMutation = useCreateCategoryMutation();
  const updateMutation = useUpdateCategoryMutation();
  const deleteMutation = useDeleteCategoryMutation();

  const [filter, setFilter] = useState("");
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState("");
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createKind, setCreateKind] = useState<"category" | "subcategory">(
    "category",
  );
  const [createName, setCreateName] = useState("");
  const [createParentId, setCreateParentId] = useState<string>("");

  const q = filter.trim().toLowerCase();
  const filteredCategories = useMemo(() => {
    if (!categories) return [];
    if (!q) return categories;
    return categories.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.children.some((s) => s.name.toLowerCase().includes(q)),
    );
  }, [categories, q]);

  const toggleExpanded = (id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const startEditing = (category: Category) => {
    setEditingId(category.id);
    setEditingName(category.name);
    setConfirmDeleteId(null);
  };

  const cancelEditing = () => {
    setEditingId(null);
    setEditingName("");
  };

  const handleSaveEdit = async (category: Category) => {
    const trimmed = editingName.trim();
    if (!trimmed || trimmed === category.name) {
      cancelEditing();
      return;
    }

    const isSubcategory = !!category.parentId;
    try {
      await updateMutation.mutateAsync({
        id: category.id,
        payload: {
          name: trimmed,
          ...(isSubcategory && category.parentId
            ? { parentId: category.parentId }
            : {}),
        },
      });
      toast({
        title: `${isSubcategory ? "Subcategory" : "Category"} renamed`,
        variant: "success",
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to rename";
      toast({
        title: "Cannot rename",
        description: message,
        variant: "destructive",
      });
    } finally {
      cancelEditing();
    }
  };

  const handleTogglePacks = async (category: Category) => {
    try {
      await updateMutation.mutateAsync({
        id: category.id,
        payload: { name: category.name, usesPacks: !category.usesPacks },
      });
      toast({
        title: !category.usesPacks ? "Packs enabled" : "Packs disabled",
        variant: "success",
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to update";
      toast({
        title: "Cannot update",
        description: message,
        variant: "destructive",
      });
    }
  };

  const handleConfirmDelete = async (category: Category) => {
    const isSubcategory = !!category.parentId;
    try {
      await deleteMutation.mutateAsync({ id: category.id });
      toast({
        title: `${isSubcategory ? "Subcategory" : "Category"} deleted`,
        description: `"${category.name}" has been removed.`,
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to delete";
      toast({
        title: "Cannot delete",
        description: message,
        variant: "destructive",
      });
    } finally {
      setConfirmDeleteId(null);
    }
  };

  const openCreateDialog = () => {
    setCreateKind("category");
    setCreateName("");
    setCreateParentId(categories?.[0]?.id ?? "");
    setCreateOpen(true);
  };

  const handleCreateSubmit = async () => {
    const trimmed = createName.trim();
    if (!trimmed) return;
    if (createKind === "subcategory" && !createParentId) {
      toast({
        title: "Pick a parent category",
        variant: "destructive",
      });
      return;
    }

    try {
      const created = await createMutation.mutateAsync({
        name: trimmed,
        ...(createKind === "subcategory"
          ? { parentId: createParentId }
          : {}),
      });
      setFilter("");
      setConfirmDeleteId(null);
      if (createKind === "subcategory") {
        setExpandedIds((prev) => new Set(prev).add(createParentId));
      }
      toast({
        title: `${createKind === "subcategory" ? "Subcategory" : "Category"} created`,
        description: `"${created.name}" has been added.`,
        variant: "success",
      });
      setCreateOpen(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : `Failed to create ${createKind}`;
      toast({
        title: "Cannot create",
        description: message,
        variant: "destructive",
      });
    }
  };

  const renderCategoryRow = (category: Category) => {
    const isEditing = editingId === category.id;
    const isConfirmingDelete = confirmDeleteId === category.id;
    const hasChildren = category.children.length > 0;
    const isExpanded = expandedIds.has(category.id) || (!!q && hasChildren);

    if (isConfirmingDelete) {
      return (
        <div className="flex min-h-[38px] items-center gap-2 rounded-[10px] border border-destructive/30 bg-destructive/10 px-[10px] py-[7px]">
          <AlertTriangle className="h-[13px] w-[13px] shrink-0 text-destructive" />
          <span className="flex-1 text-[12px] text-destructive">
            Delete &ldquo;{category.name}&rdquo;?
          </span>
          <button
            type="button"
            className={OK_BTN}
            onClick={() => handleConfirmDelete(category)}
            disabled={deleteMutation.isPending}
            aria-label="Confirm delete"
          >
            {deleteMutation.isPending ? (
              <Loader2 className="h-[13px] w-[13px] animate-spin" />
            ) : (
              <Check className="h-[13px] w-[13px]" />
            )}
          </button>
          <button
            type="button"
            className={ACT_BTN}
            onClick={() => setConfirmDeleteId(null)}
            aria-label="Cancel delete"
          >
            <X className="h-[13px] w-[13px]" />
          </button>
        </div>
      );
    }

    return (
      <div
        className={`group flex min-h-[40px] items-center gap-2 rounded-[10px] border border-transparent bg-muted/40 px-[10px] py-[7px] transition-colors hover:border-border/60 hover:bg-muted/70 ${
          isEditing ? "border-border bg-muted/80" : ""
        }`}
      >
        <button
          type="button"
          className="flex h-[18px] w-[18px] shrink-0 items-center justify-center text-muted-foreground/60 transition-transform hover:text-foreground disabled:opacity-40"
          onClick={() => toggleExpanded(category.id)}
          disabled={!hasChildren}
          aria-label={isExpanded ? "Collapse" : "Expand"}
        >
          <ChevronRight
            className={`h-3 w-3 transition-transform ${isExpanded ? "rotate-90" : ""}`}
          />
        </button>

        {isEditing ? (
          <input
            autoFocus
            value={editingName}
            onChange={(e) => setEditingName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSaveEdit(category);
              if (e.key === "Escape") cancelEditing();
            }}
            aria-label="Edit name"
            className="flex-1 bg-transparent text-[13px] text-foreground outline-none"
          />
        ) : (
          <span className="flex-1 truncate text-[13px] text-foreground">
            {category.name}
          </span>
        )}

        {hasChildren && !isEditing && (
          <span className="shrink-0 rounded-full bg-muted px-[6px] py-[1px] text-[10.5px] font-medium text-muted-foreground">
            {category.children.length}
          </span>
        )}

        <div
          className={`flex shrink-0 items-center gap-px transition-opacity ${
            isEditing ? "opacity-100" : "opacity-0 group-hover:opacity-100"
          }`}
        >
          {isEditing ? (
            <>
              <button
                type="button"
                className={OK_BTN}
                onClick={() => handleSaveEdit(category)}
                disabled={updateMutation.isPending}
                aria-label="Save"
              >
                {updateMutation.isPending ? (
                  <Loader2 className="h-[13px] w-[13px] animate-spin" />
                ) : (
                  <Check className="h-[13px] w-[13px]" />
                )}
              </button>
              <button
                type="button"
                className={ACT_BTN}
                onClick={cancelEditing}
                aria-label="Cancel"
              >
                <X className="h-[13px] w-[13px]" />
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                className={`inline-flex h-[26px] w-[26px] items-center justify-center rounded-[7px] transition-colors disabled:pointer-events-none disabled:opacity-50 ${
                  category.usesPacks
                    ? "text-emerald-600 hover:bg-destructive/10 hover:text-destructive dark:text-emerald-400"
                    : "text-muted-foreground/50 hover:bg-muted hover:text-foreground"
                }`}
                onClick={() => handleTogglePacks(category)}
                disabled={updateMutation.isPending}
                title={
                  category.usesPacks
                    ? "Sold in packs (click to disable)"
                    : "Enable packs/box tracking"
                }
                aria-label={
                  category.usesPacks ? "Disable packs" : "Enable packs"
                }
              >
                <Package className="h-[13px] w-[13px]" />
              </button>
              <div className="mx-[2px] h-[14px] w-px self-center bg-border" />
              <button
                type="button"
                className={ACT_BTN}
                onClick={() => startEditing(category)}
                title={`Rename ${category.name}`}
                aria-label="Rename"
              >
                <Pencil className="h-[13px] w-[13px]" />
              </button>
              <button
                type="button"
                className={DANGER_BTN}
                onClick={() => setConfirmDeleteId(category.id)}
                disabled={hasChildren}
                title={
                  hasChildren
                    ? "Remove subcategories first"
                    : `Delete ${category.name}`
                }
                aria-label="Delete"
              >
                <Trash2 className="h-[13px] w-[13px]" />
              </button>
            </>
          )}
        </div>
      </div>
    );
  };

  const renderSubcategoryRow = (sub: Category) => {
    const isEditing = editingId === sub.id;
    const isConfirmingDelete = confirmDeleteId === sub.id;

    if (isConfirmingDelete) {
      return (
        <div className="ml-[14px] flex min-h-[38px] items-center gap-2 rounded-[10px] border border-destructive/30 bg-destructive/10 px-[10px] py-[7px]">
          <AlertTriangle className="h-[13px] w-[13px] shrink-0 text-destructive" />
          <span className="flex-1 text-[12px] text-destructive">
            Delete &ldquo;{sub.name}&rdquo;?
          </span>
          <button
            type="button"
            className={OK_BTN}
            onClick={() => handleConfirmDelete(sub)}
            disabled={deleteMutation.isPending}
            aria-label="Confirm delete"
          >
            {deleteMutation.isPending ? (
              <Loader2 className="h-[13px] w-[13px] animate-spin" />
            ) : (
              <Check className="h-[13px] w-[13px]" />
            )}
          </button>
          <button
            type="button"
            className={ACT_BTN}
            onClick={() => setConfirmDeleteId(null)}
            aria-label="Cancel delete"
          >
            <X className="h-[13px] w-[13px]" />
          </button>
        </div>
      );
    }

    return (
      <div
        className={`group ml-[14px] flex min-h-[36px] items-center gap-2 rounded-[8px] border border-transparent bg-muted/25 py-[6px] pl-[12px] pr-[10px] transition-colors hover:border-border/40 hover:bg-muted/50 ${
          isEditing ? "border-border bg-muted/70" : ""
        }`}
      >
        <span className="mx-[3px] h-[6px] w-[6px] shrink-0 rounded-full bg-muted-foreground/40" />

        {isEditing ? (
          <input
            autoFocus
            value={editingName}
            onChange={(e) => setEditingName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSaveEdit(sub);
              if (e.key === "Escape") cancelEditing();
            }}
            aria-label="Edit name"
            className="flex-1 bg-transparent text-[12.5px] text-foreground outline-none"
          />
        ) : (
          <span className="flex-1 truncate text-[12.5px] text-muted-foreground">
            {sub.name}
          </span>
        )}

        <div
          className={`flex shrink-0 items-center gap-px transition-opacity ${
            isEditing ? "opacity-100" : "opacity-0 group-hover:opacity-100"
          }`}
        >
          {isEditing ? (
            <>
              <button
                type="button"
                className={OK_BTN}
                onClick={() => handleSaveEdit(sub)}
                disabled={updateMutation.isPending}
                aria-label="Save"
              >
                {updateMutation.isPending ? (
                  <Loader2 className="h-[13px] w-[13px] animate-spin" />
                ) : (
                  <Check className="h-[13px] w-[13px]" />
                )}
              </button>
              <button
                type="button"
                className={ACT_BTN}
                onClick={cancelEditing}
                aria-label="Cancel"
              >
                <X className="h-[13px] w-[13px]" />
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                className={ACT_BTN}
                onClick={() => startEditing(sub)}
                title={`Rename ${sub.name}`}
                aria-label="Rename"
              >
                <Pencil className="h-[13px] w-[13px]" />
              </button>
              <button
                type="button"
                className={DANGER_BTN}
                onClick={() => setConfirmDeleteId(sub.id)}
                title={`Delete ${sub.name}`}
                aria-label="Delete"
              >
                <Trash2 className="h-[13px] w-[13px]" />
              </button>
            </>
          )}
        </div>
      </div>
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[80vh] w-full flex-col gap-0 overflow-hidden rounded-[20px] p-0 sm:max-w-[420px]">
        <DialogHeader className="space-y-[3px] border-b px-[18px] pb-[14px] pt-[18px]">
          <DialogTitle className="text-[16px] font-medium leading-tight tracking-[-0.01em]">
            Manage categories
          </DialogTitle>
          <DialogDescription className="text-[12px] leading-[1.5]">
            Create, rename, or delete categories and subcategories.
          </DialogDescription>
        </DialogHeader>

        <div className="relative px-[18px] pt-[10px]">
          <Search className="pointer-events-none absolute left-[31px] top-1/2 h-[14px] w-[14px] -translate-y-1/2 text-muted-foreground/60" />
          <input
            type="text"
            placeholder="Search…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            aria-label="Search categories"
            className="w-full rounded-[10px] border bg-muted/40 px-3 py-[7px] pl-[32px] text-[13px] text-foreground outline-none transition-colors placeholder:text-muted-foreground/60 focus:border-ring focus:bg-muted/60"
          />
        </div>

        <div className="flex max-h-[400px] flex-1 flex-col gap-[3px] overflow-y-auto px-[18px] py-[8px]">
          {isLoading ? (
            <div className="flex items-center justify-center py-8 text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              <span className="text-[13px]">Loading categories...</span>
            </div>
          ) : filteredCategories.length === 0 ? (
            <p className="py-5 text-center text-[12.5px] text-muted-foreground">
              {q ? "No categories found" : "No categories yet"}
            </p>
          ) : (
            filteredCategories.map((category) => {
              const hasChildren = category.children.length > 0;
              const isExpanded =
                expandedIds.has(category.id) || (!!q && hasChildren);
              const visibleSubs = q
                ? category.children.filter(
                    (s) =>
                      s.name.toLowerCase().includes(q) ||
                      category.name.toLowerCase().includes(q),
                  )
                : category.children;

              return (
                <div key={category.id} className="flex flex-col gap-[2px]">
                  {renderCategoryRow(category)}
                  {isExpanded &&
                    visibleSubs.map((sub) => (
                      <div key={sub.id}>{renderSubcategoryRow(sub)}</div>
                    ))}
                </div>
              );
            })
          )}
        </div>

        <div className="border-t px-[18px] pb-[16px] pt-[10px]">
          <button
            type="button"
            onClick={openCreateDialog}
            className="flex w-full items-center justify-center gap-[6px] rounded-[10px] border bg-muted/40 px-[14px] py-[9px] text-[13px] text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground disabled:opacity-50"
          >
            <Plus className="h-[14px] w-[14px]" />
            Add category
          </button>
        </div>
      </DialogContent>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="w-full rounded-[20px] p-0 sm:max-w-[400px]">
          <DialogHeader className="space-y-[3px] border-b px-[18px] pb-[14px] pt-[18px]">
            <DialogTitle className="text-[16px] font-medium leading-tight tracking-[-0.01em]">
              New {createKind}
            </DialogTitle>
            <DialogDescription className="text-[12px] leading-[1.5]">
              Choose a type and give it a name.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-[14px] px-[18px] py-[14px]">
            <div className="flex flex-col gap-[6px]">
              <span className="text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
                Type
              </span>
              <div className="inline-flex rounded-[10px] border bg-muted/40 p-[3px]">
                {(["category", "subcategory"] as const).map((kind) => (
                  <button
                    key={kind}
                    type="button"
                    onClick={() => setCreateKind(kind)}
                    className={`flex-1 rounded-[7px] px-[10px] py-[6px] text-[12.5px] capitalize transition-colors ${
                      createKind === kind
                        ? "bg-background text-foreground shadow-sm"
                        : "text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    {kind}
                  </button>
                ))}
              </div>
            </div>

            {createKind === "subcategory" && (
              <div className="flex flex-col gap-[6px]">
                <span className="text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
                  Parent category
                </span>
                <Select
                  value={createParentId}
                  onValueChange={setCreateParentId}
                >
                  <SelectTrigger className="w-full text-[13px]">
                    <SelectValue placeholder="Select a category" />
                  </SelectTrigger>
                  <SelectContent>
                    {(categories ?? []).map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            <div className="flex flex-col gap-[6px]">
              <span className="text-[11.5px] font-medium uppercase tracking-wide text-muted-foreground">
                Name
              </span>
              <input
                autoFocus
                type="text"
                value={createName}
                onChange={(e) => setCreateName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleCreateSubmit();
                }}
                placeholder={`e.g. ${
                  createKind === "subcategory" ? "Foil singles" : "Singles"
                }`}
                className="w-full rounded-[10px] border bg-muted/40 px-3 py-[8px] text-[13px] text-foreground outline-none transition-colors placeholder:text-muted-foreground/60 focus:border-ring focus:bg-muted/60"
              />
            </div>
          </div>

          <DialogFooter className="flex-row justify-end gap-[8px] border-t px-[18px] py-[12px] sm:justify-end">
            <button
              type="button"
              onClick={() => setCreateOpen(false)}
              className="rounded-[10px] border px-[14px] py-[7px] text-[13px] text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleCreateSubmit}
              disabled={
                createMutation.isPending ||
                !createName.trim() ||
                (createKind === "subcategory" && !createParentId)
              }
              className="inline-flex items-center gap-[6px] rounded-[10px] bg-foreground px-[14px] py-[7px] text-[13px] text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {createMutation.isPending ? (
                <Loader2 className="h-[13px] w-[13px] animate-spin" />
              ) : (
                <Plus className="h-[13px] w-[13px]" />
              )}
              Create
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Dialog>
  );
}
