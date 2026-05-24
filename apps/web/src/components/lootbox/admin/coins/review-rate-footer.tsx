"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useCoinEconomyConfig } from "@/hooks/queries/use-lootbox";
import { EditRateDialog } from "./edit-rate-dialog";

/** Compact horizontal footer card for the review-to-coin rate. */
export function ReviewRateFooter() {
  const configQuery = useCoinEconomyConfig();
  const [editOpen, setEditOpen] = useState(false);
  const config = configQuery.data;

  return (
    <section className="flex items-center justify-between gap-3 rounded-[10px] border border-border bg-card px-4 py-3.5">
      <div className="flex min-w-0 items-center gap-3.5">
        <div
          className="flex h-9 w-9 flex-none items-center justify-center rounded-lg border font-mono text-[14px] font-semibold text-brand-primary"
          style={{
            background: "rgba(139,92,246,0.12)",
            borderColor: "rgba(139,92,246,0.28)",
          }}
        >
          {configQuery.isLoading || !config ? "—" : `${config.reviewCoinRate}×`}
        </div>
        <div className="min-w-0">
          <div className="text-[13px] font-medium text-foreground">Review coin rate</div>
          {configQuery.isLoading ? (
            <Skeleton className="mt-1 h-3 w-72" />
          ) : config ? (
            <div className="font-mono text-[11px] text-muted-foreground">
              {config.reviewCoinRate} coin{config.reviewCoinRate === 1 ? "" : "s"} per 5★ review with @mention · {config.nextFetchHint}
            </div>
          ) : (
            <div className="text-[11px] text-rose-500">Failed to load coin economy config.</div>
          )}
        </div>
      </div>
      <Button
        size="sm"
        variant="outline"
        onClick={() => setEditOpen(true)}
        disabled={configQuery.isLoading || !config}
        className="flex-none"
      >
        Edit
      </Button>

      <EditRateDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        currentRate={config?.reviewCoinRate ?? null}
        hint={config?.nextFetchHint ?? null}
      />
    </section>
  );
}
