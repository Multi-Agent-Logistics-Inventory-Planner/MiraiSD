"use client";

import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useState,
  type ReactNode,
} from "react";
import { ArrowLeft, Copy, Layers, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { isUploadError, uploadProductImage } from "@/lib/storage/images";
import { useToast } from "@/hooks/use-toast";
import { useLastClosedKujiTiers } from "@/hooks/queries/use-kuji-box";
import type { ProductListItem } from "@/types/api";
import {
  blankTier,
  EMPTY_LOCATION,
  validateTier,
  type DraftTier,
} from "@/components/kuji/tier-draft";
import {
  TierPanel,
  TierSidebarItem,
} from "@/components/kuji/tier-draft-ui";

export interface ProcessedTier {
  readonly tempId: string;
  readonly label: string;
  readonly letter: string | null;
  readonly mode: "create" | "existing";
  readonly linkedProductId: string | null;
  readonly sourceLocationId: string | null;
  readonly activeCount: number;
  readonly inactiveCount: number;
  readonly price: number | null;
  readonly productName: string | null;
  readonly productImageUrl: string | null;
}

export interface TierDraftEditorHandle {
  submitTiers: () => Promise<ProcessedTier[] | null>;
  focusTier: (tempId: string) => void;
}

interface TierDraftEditorProps {
  readonly productId: string;
  readonly isPending: boolean;
  /** Change this value to reset the editor (e.g. when the dialog opens). */
  readonly resetSignal: unknown;
  /** Optional header rendered above the tier list in the sidebar. */
  readonly sidebarHeader?: ReactNode;
  /** Errors keyed by the same `tier:<tempId>:<field>` convention to surface alongside local errors. */
  readonly extraErrors?: Record<string, string>;
}

export const TierDraftEditor = forwardRef<
  TierDraftEditorHandle,
  TierDraftEditorProps
>(function TierDraftEditor(
  { productId, isPending, resetSignal, sidebarHeader, extraErrors },
  ref,
) {
  const { toast } = useToast();
  const lastTiersQuery = useLastClosedKujiTiers(productId || null);

  const [tiers, setTiers] = useState<DraftTier[]>([]);
  const [activeTempId, setActiveTempId] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [mobileShowMenu, setMobileShowMenu] = useState(true);

  function selectTier(tempId: string) {
    setActiveTempId(tempId);
    setMobileShowMenu(false);
  }

  useEffect(() => {
    const seed = blankTier();
    setTiers([seed]);
    setActiveTempId(seed.tempId);
    setErrors({});
    setMobileShowMenu(true);
  }, [resetSignal]);

  function handleCloneFromLast() {
    const data = lastTiersQuery.data;
    if (!data || data.length === 0) {
      toast({
        title: "No previous box",
        description: "There is no closed box to clone from.",
      });
      return;
    }
    const cloned: DraftTier[] = data.map((t) => ({
      tempId: crypto.randomUUID(),
      label: t.label,
      letter: t.letter ?? "",
      mode: t.linkedProductId ? "existing" : "create",
      linkedProductId: t.linkedProductId ?? "",
      linkedProductDisplayName: t.linkedProductName ?? "",
      linkedProductDisplaySku: null,
      source: EMPTY_LOCATION,
      productName: "",
      productImageFile: null,
      productImagePreviewUrl: null,
      productImageUrl: "",
      count: t.activeCount,
      heldBack: t.inactiveCount > 0 ? t.inactiveCount : "",
      price: t.price != null ? String(t.price) : "",
    }));
    setTiers(cloned);
    if (cloned[0]) selectTier(cloned[0].tempId);
    else setActiveTempId(null);
    toast({ title: `Cloned ${data.length} tier(s)`, variant: "success" });
  }

  function handleAddTier() {
    const next = blankTier();
    setTiers((prev) => [...prev, next]);
    selectTier(next.tempId);
  }

  function handleRemoveTier(tempId: string) {
    setTiers((prev) => {
      if (prev.length <= 1) return prev;
      const idx = prev.findIndex((t) => t.tempId === tempId);
      const next = prev.filter((t) => t.tempId !== tempId);
      if (activeTempId === tempId) {
        const fallback = next[idx] ?? next[idx - 1] ?? next[0];
        setActiveTempId(fallback?.tempId ?? null);
      }
      return next;
    });
  }

  function clearTierErrors(tempId: string) {
    setErrors((prev) => {
      const next: Record<string, string> = {};
      for (const k of Object.keys(prev)) {
        if (!k.startsWith(`tier:${tempId}:`)) next[k] = prev[k];
      }
      return next;
    });
  }

  function updateTier(tempId: string, updates: Partial<DraftTier>) {
    setTiers((prev) =>
      prev.map((t) => (t.tempId === tempId ? { ...t, ...updates } : t)),
    );
    clearTierErrors(tempId);
  }

  function handleLinkedProductSelect(tempId: string, product: ProductListItem) {
    setTiers((prev) =>
      prev.map((t) => {
        if (t.tempId !== tempId) return t;
        return {
          ...t,
          linkedProductId: product.id,
          linkedProductDisplayName: product.name,
          linkedProductDisplaySku: product.sku ?? null,
          source: EMPTY_LOCATION,
          price: product.msrp != null ? String(product.msrp) : t.price,
        };
      }),
    );
    clearTierErrors(tempId);
  }

  useImperativeHandle(
    ref,
    () => ({
      async submitTiers() {
        const nextErrors: Record<string, string> = {};
        if (tiers.length === 0) {
          nextErrors.tiers = "At least one tier is required";
        }
        tiers.forEach((t) => validateTier(t, nextErrors));
        setErrors(nextErrors);
        if (Object.keys(nextErrors).length > 0) {
          const firstBad = tiers.find((t) =>
            Object.keys(nextErrors).some((k) =>
              k.startsWith(`tier:${t.tempId}:`),
            ),
          );
          if (firstBad) selectTier(firstBad.tempId);
          return null;
        }

        const uploadedUrlByTempId = new Map<string, string>();
        try {
          for (const t of tiers) {
            if (t.mode === "create" && t.productImageFile) {
              const result = await uploadProductImage(t.productImageFile);
              if (isUploadError(result)) {
                toast({
                  title: "Image upload failed",
                  description: result.message,
                });
                return null;
              }
              uploadedUrlByTempId.set(t.tempId, result.url);
            }
          }
        } catch (err: unknown) {
          const message =
            err instanceof Error ? err.message : "Image upload failed";
          toast({ title: "Image upload failed", description: message });
          return null;
        }

        return tiers.map((t): ProcessedTier => {
          const isCreate = t.mode === "create";
          const uploadedImageUrl =
            uploadedUrlByTempId.get(t.tempId) ?? (t.productImageUrl || null);
          const parsedPrice = t.price === "" ? null : parseFloat(t.price);
          return {
            tempId: t.tempId,
            label: t.label.trim(),
            letter: t.letter.trim() || null,
            mode: t.mode,
            linkedProductId: isCreate ? null : t.linkedProductId || null,
            sourceLocationId:
              !isCreate && t.linkedProductId ? t.source.locationId : null,
            activeCount: t.count === "" ? 0 : t.count,
            inactiveCount:
              typeof t.heldBack === "number" ? t.heldBack : 0,
            price: parsedPrice,
            productName: isCreate ? t.productName.trim() : null,
            productImageUrl: isCreate ? uploadedImageUrl : null,
          };
        });
      },
      focusTier(tempId: string) {
        selectTier(tempId);
      },
    }),
    [tiers, toast],
  );

  const hasLastTiers = (lastTiersQuery.data?.length ?? 0) > 0;
  const activeTier = tiers.find((t) => t.tempId === activeTempId) ?? null;
  const activeIndex = activeTier
    ? tiers.findIndex((t) => t.tempId === activeTier.tempId)
    : -1;
  const mergedErrors = { ...errors, ...(extraErrors ?? {}) };

  return (
    <div className="flex flex-1 min-h-0 overflow-hidden">
      <aside
        className={cn(
          "w-full sm:w-[220px] shrink-0 bg-muted/40 sm:border-r flex flex-col overflow-hidden",
          !mobileShowMenu && "hidden sm:flex",
        )}
      >
        {sidebarHeader}

        <div className="flex items-center justify-between px-3 pt-3 pb-1.5">
          <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
            Tiers
          </span>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-6 px-2 text-xs text-muted-foreground"
            onClick={handleCloneFromLast}
            disabled={isPending || lastTiersQuery.isLoading || !hasLastTiers}
            title={
              hasLastTiers
                ? "Clone tiers from last closed box"
                : "No previous box to clone"
            }
          >
            <Copy className="h-3 w-3 mr-1" />
            Clone last
          </Button>
        </div>

        <div className="flex-1 overflow-y-auto px-2 pb-2">
          {tiers.map((tier, idx) => (
            <TierSidebarItem
              key={tier.tempId}
              tier={tier}
              index={idx}
              active={tier.tempId === activeTempId}
              canRemove={tiers.length > 1}
              disabled={isPending}
              onSelect={() => selectTier(tier.tempId)}
              onRemove={() => handleRemoveTier(tier.tempId)}
            />
          ))}
          {mergedErrors.tiers ? (
            <p className="text-xs text-destructive px-2 py-2">
              {mergedErrors.tiers}
            </p>
          ) : null}
        </div>

        <button
          type="button"
          onClick={handleAddTier}
          disabled={isPending}
          className="flex items-center justify-center gap-1.5 px-3 py-2.5 border-t text-sm text-muted-foreground hover:text-foreground hover:bg-muted/60 disabled:opacity-50 transition-colors"
        >
          <Plus className="h-4 w-4" />
          Add tier
        </button>
      </aside>

      <div
        className={cn(
          "flex-1 overflow-y-auto min-w-0",
          mobileShowMenu && "hidden sm:block",
        )}
      >
        <div className="sm:hidden border-b">
          <button
            type="button"
            onClick={() => setMobileShowMenu(true)}
            className="flex items-center gap-1.5 px-4 py-2.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
        </div>
        {activeTier ? (
          <TierPanel
            tier={activeTier}
            index={activeIndex}
            errors={mergedErrors}
            isPending={isPending}
            onUpdate={updateTier}
            onLinkedProductSelect={handleLinkedProductSelect}
          />
        ) : (
          <div className="h-full flex flex-col items-center justify-center gap-2 text-muted-foreground p-8">
            <Layers className="h-8 w-8 opacity-40" />
            <span className="text-sm">Select a tier to configure it</span>
          </div>
        )}
      </div>
    </div>
  );
});
