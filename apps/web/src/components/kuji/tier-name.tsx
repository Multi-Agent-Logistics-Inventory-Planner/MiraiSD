"use client";

import type { KujiBoxTier } from "@/types/api";
import { useTierClassColor } from "./tier-class-color-context";

interface TierNameProps {
  readonly tier: Pick<KujiBoxTier, "label" | "letter" | "linkedProductName">;
  readonly className?: string;
  readonly colorSecondary?: boolean;
}

export function TierName({ tier, className, colorSecondary }: TierNameProps) {
  const productName = tier.linkedProductName?.trim();
  const label = tier.label?.trim() ?? "";
  const hasProduct =
    !!productName && productName.toLowerCase() !== label.toLowerCase();
  const primary = hasProduct ? productName : label;
  const secondary = hasProduct ? label : null;
  const secondaryColor = useTierClassColor(secondary);
  const secondaryStyle =
    colorSecondary && secondary ? { color: secondaryColor } : undefined;

  return (
    <span className={className ?? "min-w-0 flex-1 truncate text-xs"}>
      {tier.letter ? (
        <span className="mr-1.5 font-mono text-muted-foreground">
          {tier.letter}
        </span>
      ) : null}
      <span>{primary}</span>
      {secondary ? (
        <span
          className={
            colorSecondary
              ? "ml-1.5 font-medium"
              : "ml-1.5 text-muted-foreground"
          }
          style={secondaryStyle}
        >
          {secondary}
        </span>
      ) : null}
    </span>
  );
}
