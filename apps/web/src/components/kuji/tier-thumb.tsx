"use client";

import { useState } from "react";
import Image from "next/image";
import type { KujiBoxTier } from "@/types/api";
import { getSafeImageUrl } from "@/lib/utils/validation";
import { hexWithAlpha, tierColor } from "./tier-palette";

export interface TierThumbExpand {
  url: string;
  alt: string;
}

interface TierThumbProps {
  readonly tier: KujiBoxTier;
  readonly rank: number;
  readonly size?: number;
  readonly dashed?: boolean;
  readonly onExpand?: (state: TierThumbExpand) => void;
}

export function TierThumb({
  tier,
  rank,
  size = 40,
  dashed = false,
  onExpand,
}: TierThumbProps) {
  const color = tierColor(rank);
  const safeUrl = getSafeImageUrl(tier.linkedProductImageUrl);
  const [errored, setErrored] = useState(false);
  const dimension = `${size}px`;

  if (safeUrl && !errored) {
    const alt = tier.linkedProductName ?? tier.label;
    const interactive = !!onExpand;
    const commonClasses =
      "group relative shrink-0 overflow-hidden rounded-md" +
      (interactive
        ? " cursor-zoom-in focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        : "");
    const ringStyle = dashed
      ? { outline: `1px dashed ${color}`, outlineOffset: -1 }
      : { boxShadow: `inset 0 0 0 1px ${color}` };

    const inner = (
      <Image
        src={safeUrl}
        alt={alt}
        fill
        sizes={`${size * 2}px`}
        className="object-cover transition-opacity group-hover:opacity-90"
        onError={() => setErrored(true)}
      />
    );

    if (interactive) {
      return (
        <button
          type="button"
          onClick={() => onExpand({ url: safeUrl, alt })}
          aria-label={`Expand image of ${alt}`}
          className={commonClasses}
          style={{ width: dimension, height: dimension, ...ringStyle }}
        >
          {inner}
        </button>
      );
    }

    return (
      <div
        className={commonClasses}
        style={{ width: dimension, height: dimension, ...ringStyle }}
      >
        {inner}
      </div>
    );
  }

  const alphaBg = hexWithAlpha(color, dashed ? 0.1 : 0.18);
  return (
    <div
      className="flex shrink-0 items-center justify-center rounded-md"
      style={{
        width: dimension,
        height: dimension,
        background: alphaBg,
      }}
      aria-hidden
    >
      <svg
        width={Math.max(12, Math.round(size * 0.4))}
        height={Math.max(12, Math.round(size * 0.4))}
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
          strokeDasharray={dashed ? "2.5 1.5" : undefined}
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
