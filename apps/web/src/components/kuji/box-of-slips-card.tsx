"use client";

import { useMemo, useState } from "react";
import Image from "next/image";
import { Maximize2 } from "lucide-react";
import type { KujiBoxTier } from "@/types/api";
import { getSafeImageUrl } from "@/lib/utils/validation";
import { ProductImageLightbox } from "@/components/products/product-image-lightbox";
import { compareTiers, hexWithAlpha, tierColor } from "./tier-palette";
import { TierName } from "./tier-name";

interface BoxOfSlipsCardProps {
  readonly tiers: readonly KujiBoxTier[];
  readonly totalCount: number;
}

interface LightboxState {
  url: string;
  alt: string;
}

function formatChance(count: number, totalCount: number): string {
  if (totalCount <= 0) return "0%";
  const pct = (count / totalCount) * 100;
  return `${pct.toFixed(0)}%`;
}

interface TierThumbProps {
  tier: KujiBoxTier;
  rank: number;
  onExpand: (state: LightboxState) => void;
}

function TierThumb({ tier, rank, onExpand }: TierThumbProps) {
  const color = tierColor(rank);
  const safeUrl = getSafeImageUrl(tier.linkedProductImageUrl);
  const [errored, setErrored] = useState(false);

  if (safeUrl && !errored) {
    const alt = tier.linkedProductName ?? tier.label;
    return (
      <button
        type="button"
        onClick={() => onExpand({ url: safeUrl, alt })}
        aria-label={`Expand image of ${alt}`}
        className="group relative h-10 w-10 shrink-0 overflow-hidden rounded-md cursor-zoom-in focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        style={{ boxShadow: `inset 0 0 0 1px ${color}` }}
      >
        <Image
          src={safeUrl}
          alt={alt}
          fill
          sizes="40px"
          className="object-cover transition-opacity group-hover:opacity-90"
          onError={() => setErrored(true)}
        />
        <span className="pointer-events-none absolute bottom-0.5 right-0.5 flex h-4 w-4 items-center justify-center rounded bg-black/60 text-white opacity-90 group-hover:opacity-100">
          <Maximize2 className="h-2.5 w-2.5" />
        </span>
      </button>
    );
  }

  return (
    <div
      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md"
      style={{ background: hexWithAlpha(color, 0.18) }}
      aria-hidden
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 12 12"
        fill="none"
      >
        <rect
          x="1"
          y="2"
          width="10"
          height="8"
          rx="1.5"
          stroke={color}
          strokeWidth="1.2"
        />
        <line
          x1="3"
          y1="5"
          x2="9"
          y2="5"
          stroke={color}
          strokeWidth="1.2"
          strokeLinecap="round"
        />
        <line
          x1="3"
          y1="7.5"
          x2="7"
          y2="7.5"
          stroke={color}
          strokeWidth="1.2"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}

export function BoxOfSlipsCard({ tiers, totalCount }: BoxOfSlipsCardProps) {
  const sorted = useMemo(
    () =>
      [...tiers]
        .sort(compareTiers)
        .filter((t) => t.activeCount > 0),
    [tiers],
  );
  const [lightbox, setLightbox] = useState<LightboxState | null>(null);

  return (
    <div className="rounded-xl border bg-card p-4 dark:border-none">
      <div className="mb-3 flex items-center justify-between border-b pb-2.5">
        <span className="text-sm font-medium">Active Slips</span>
        <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground tabular-nums">
          {totalCount} slip{totalCount === 1 ? "" : "s"}
        </span>
      </div>
      {sorted.length === 0 ? (
        <div className="py-6 text-center text-xs text-muted-foreground">
          Box is empty.
        </div>
      ) : (
        <div className="max-h-80 overflow-y-auto pr-1">
          <ul className="divide-y">
            {sorted.map((tier, idx) => (
              <li
                key={tier.id}
                className="flex items-center gap-3 py-2"
              >
                <TierThumb tier={tier} rank={idx} onExpand={setLightbox} />
                <TierName tier={tier} />
                <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground tabular-nums">
                  {tier.activeCount} · {formatChance(tier.activeCount, totalCount)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
      <ProductImageLightbox
        open={lightbox !== null}
        onOpenChange={(open) => {
          if (!open) setLightbox(null);
        }}
        imageUrl={lightbox?.url}
        alt={lightbox?.alt ?? ""}
      />
    </div>
  );
}
