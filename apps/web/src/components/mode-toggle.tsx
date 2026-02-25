"use client";

import { useTheme } from "next-themes";
import { Sun, Moon, Monitor } from "lucide-react";

const THEME_CYCLE = ["light", "dark", "system"] as const;

const THEME_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  light: Sun,
  dark: Moon,
  system: Monitor,
};

export function ModeToggle() {
  const { theme, setTheme } = useTheme();

  const current = theme ?? "system";
  const currentIndex = THEME_CYCLE.indexOf(current as (typeof THEME_CYCLE)[number]);
  const nextTheme = THEME_CYCLE[(currentIndex + 1) % THEME_CYCLE.length];
  const Icon = THEME_ICONS[current] ?? Monitor;

  return (
    <button
      onClick={() => setTheme(nextTheme)}
      className="text-muted-foreground hover:text-foreground cursor-pointer"
      title={`Theme: ${current} (click for ${nextTheme})`}
      aria-label={`Switch to ${nextTheme} theme`}
    >
      <Icon className="h-4 w-4" />
    </button>
  );
}
