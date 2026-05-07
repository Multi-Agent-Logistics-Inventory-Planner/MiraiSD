"use client";

interface KujiStatTileProps {
  readonly label: string;
  readonly value: number | string;
  readonly sub?: string;
}

export function KujiStatTile({ label, value, sub }: KujiStatTileProps) {
  return (
    <div className="rounded-xl border bg-card p-4 dark:border-none">
      <div className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </div>
      <div className="mt-1 text-2xl font-medium tabular-nums">{value}</div>
      {sub ? (
        <div className="mt-1 text-[11px] text-muted-foreground">{sub}</div>
      ) : null}
    </div>
  );
}
