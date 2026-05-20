"use client";

import { hexWithAlpha } from "@/components/kuji/tier-palette";

interface CrateIllustrationProps {
  readonly size?: number;
  readonly tint?: string;
}

export function CrateIllustration({ size = 180, tint = "#7c3aed" }: CrateIllustrationProps) {
  const stroke = {
    stroke: tint,
    strokeWidth: 1.4,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    fill: "none",
  };
  return (
    <div
      className="relative flex items-center justify-center"
      style={{ width: size, height: size }}
    >
      <div
        aria-hidden
        className="absolute inset-0 rounded-full"
        style={{
          background: `radial-gradient(circle at 50% 60%, ${hexWithAlpha(tint, 0.35)} 0%, transparent 60%)`,
        }}
      />
      <svg width={size} height={size} viewBox="0 0 180 180" className="relative">
        <path {...stroke} d="M40 70 L90 45 L140 70 L140 130 L90 155 L40 130 Z" />
        <path {...stroke} d="M40 70 L90 95 L140 70" />
        <path {...stroke} d="M90 95 L90 155" />
        <path {...stroke} d="M65 57.5 L65 117.5 L90 130" />
        <path {...stroke} d="M115 57.5 L115 117.5 L90 130" />
        <rect
          {...stroke}
          x="80"
          y="83"
          width="20"
          height="14"
          rx="1.5"
          fill={hexWithAlpha(tint, 0.18)}
        />
        <path {...stroke} d="M86 90 L94 90" />
      </svg>
    </div>
  );
}
