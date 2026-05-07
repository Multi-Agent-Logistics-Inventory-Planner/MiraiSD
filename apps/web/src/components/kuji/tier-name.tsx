import type { KujiBoxTier } from "@/types/api";

interface TierNameProps {
  readonly tier: Pick<KujiBoxTier, "label" | "letter" | "linkedProductName">;
  readonly className?: string;
}

export function TierName({ tier, className }: TierNameProps) {
  const productName = tier.linkedProductName?.trim();
  const label = tier.label?.trim() ?? "";
  const showProduct =
    productName && productName.toLowerCase() !== label.toLowerCase();

  return (
    <span className={className ?? "min-w-0 flex-1 truncate text-xs"}>
      {tier.letter ? (
        <span className="mr-1.5 font-mono text-muted-foreground">
          {tier.letter}
        </span>
      ) : null}
      <span>{label}</span>
      {showProduct ? (
        <span className="ml-1.5 text-muted-foreground">{productName}</span>
      ) : null}
    </span>
  );
}
