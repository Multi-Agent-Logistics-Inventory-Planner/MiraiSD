import { cn } from "@/lib/utils";

interface BarStatProps {
  readonly label: string;
  readonly value: string;
  readonly sub?: string;
  readonly valueClassName?: string;
  readonly align?: "left" | "right";
}

export function BarStat({
  label,
  value,
  sub,
  valueClassName,
  align = "left",
}: BarStatProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-1",
        align === "right" ? "items-end text-right" : "items-start"
      )}
    >
      <span className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      <span
        className={cn(
          "text-[26px] font-medium leading-none tabular-nums",
          valueClassName
        )}
      >
        {value}
      </span>
      {sub && (
        <span className="text-[11px] text-muted-foreground tabular-nums">
          {sub}
        </span>
      )}
    </div>
  );
}
