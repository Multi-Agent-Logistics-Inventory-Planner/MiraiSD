"use client";

import { cn } from "@/lib/utils";
import type { UrgencyFilter } from "./types";

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
        {tabs.map((tab) => (
          <button
            key={tab.value}
            onClick={() => onTabChange(tab.value)}
            className={cn(
              "shrink-0 whitespace-nowrap pb-2 text-xs sm:text-sm font-medium transition-colors relative cursor-pointer",
              activeTab === tab.value
                ? "text-[#0b66c2] dark:text-[#7c3aed]"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            {tab.label} ({tab.count})
            {activeTab === tab.value && (
              <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#0b66c2] dark:bg-[#7c3aed]" />
            )}
          </button>
        ))}
      </div>
    </div>
  );
}
