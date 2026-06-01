"use client";

import { cn } from "@/lib/utils";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import type { UrgencyFilter } from "./types";
import { TAB_TOOLTIPS } from "./help-content";

interface UrgencyTab {
  value: UrgencyFilter;
  label: string;
  count: number;
}

interface UrgencyTabsProps {
  tabs: UrgencyTab[];
  activeTab: UrgencyFilter;
  onTabChange: (tab: UrgencyFilter) => void;
}

export function UrgencyTabs({ tabs, activeTab, onTabChange }: UrgencyTabsProps) {
  return (
    <div className="relative">
      <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />
      <div className="flex gap-6 overflow-x-auto scrollbar-none border-b dark:border-b-[0.5px] pr-6">
        {tabs.map((tab) => {
          const active = activeTab === tab.value;
          return (
            <Tooltip key={tab.value}>
              <TooltipTrigger asChild>
                <button
                  onClick={() => onTabChange(tab.value)}
                  className={cn(
                    "shrink-0 inline-flex items-center gap-2 whitespace-nowrap pb-2 text-xs sm:text-sm transition-colors relative cursor-pointer",
                    active
                      ? "font-semibold text-foreground"
                      : "font-medium text-muted-foreground hover:text-foreground",
                  )}
                >
                  <span>{tab.label}</span>
                  <span
                    className={cn(
                      "inline-flex items-center rounded font-mono text-[11px] px-1.5 py-[1px] tabular-nums",
                      active
                        ? "bg-violet-100 text-violet-700 dark:bg-violet-400/15 dark:text-violet-300"
                        : "bg-muted text-muted-foreground",
                    )}
                  >
                    {tab.count}
                  </span>
                  {active && (
                    <span className="absolute -bottom-px left-0 right-0 h-0.5 bg-[#0b66c2] dark:bg-[#a78bfa]" />
                  )}
                </button>
              </TooltipTrigger>
              <TooltipContent side="bottom">
                {TAB_TOOLTIPS[tab.value]}
              </TooltipContent>
            </Tooltip>
          );
        })}
      </div>
    </div>
  );
}
