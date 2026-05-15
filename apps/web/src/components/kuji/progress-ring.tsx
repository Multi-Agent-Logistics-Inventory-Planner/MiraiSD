interface ProgressRingProps {
  readonly value: number;
  readonly total: number;
  readonly size?: number;
  readonly stroke?: number;
  readonly label?: string;
}

export function ProgressRing({
  value,
  total,
  size = 88,
  stroke = 8,
  label,
}: ProgressRingProps) {
  const pct = total > 0 ? Math.max(0, Math.min(1, value / total)) : 0;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - pct);
  const display = `${Math.round(pct * 100)}%`;

  return (
    <div
      className="relative flex shrink-0 items-center justify-center"
      style={{ width: size, height: size }}
    >
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={stroke}
          className="fill-none stroke-border/50"
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={stroke}
          strokeLinecap="round"
          className="fill-none stroke-brand-primary transition-[stroke-dashoffset] duration-500"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
        <span className="text-base font-medium tabular-nums leading-none">
          {display}
        </span>
        {label && (
          <span className="mt-0.5 text-[10px] uppercase tracking-wider text-muted-foreground">
            {label}
          </span>
        )}
      </div>
    </div>
  );
}
