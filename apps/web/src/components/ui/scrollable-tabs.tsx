"use client";

import type { LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";

export interface TabConfig<T extends string> {
  value: T;
  label: string;
  icon: LucideIcon | string;
  adminOnly?: boolean;
}

interface ScrollableTabsProps<T extends string> {
  tabs: TabConfig<T>[];
  value: T;
  onValueChange: (value: T) => void;
  isAdmin?: boolean;
}

export function ScrollableTabs<T extends string>({
  tabs,
  value,
  onValueChange,
  isAdmin = false,
}: ScrollableTabsProps<T>) {
  const visibleTabs = tabs.filter((tab) => !tab.adminOnly || isAdmin);

  return (
    <div className="relative">
      {/* Scroll fade edge (right only) */}
      <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />

      <div className="flex gap-1.5 overflow-x-auto scrollbar-none pb-1 pr-6">
        {visibleTabs.map(({ value: tabValue, label, icon: Icon }) => {
          const active = value === tabValue;
          return (
            <Button
              key={tabValue}
              variant={active ? "default" : "outline"}
              size="sm"
              onClick={() => onValueChange(tabValue)}
              className={
                active
                  ? "bg-brand-primary text-white hover:bg-brand-primary/90"
                  : "bg-[#e1e1e1] dark:bg-[#30302e] border-none dark:text-[#9b9b9a]"
              }
            >
              {typeof Icon === "string" ? (
                <span
                  aria-hidden
                  className="inline-block h-4.5 w-4.5 bg-current"
                  style={{
                    WebkitMaskImage: `url(${Icon})`,
                    maskImage: `url(${Icon})`,
                    WebkitMaskRepeat: "no-repeat",
                    maskRepeat: "no-repeat",
                    WebkitMaskSize: "contain",
                    maskSize: "contain",
                    WebkitMaskPosition: "center",
                    maskPosition: "center",
                  }}
                />
              ) : (
                <Icon className="h-3.5 w-3.5" />
              )}
              {label}
            </Button>
          );
        })}
      </div>
    </div>
  );
}
