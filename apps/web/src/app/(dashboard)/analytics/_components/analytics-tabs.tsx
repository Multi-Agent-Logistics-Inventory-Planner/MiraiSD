"use client";

import {
  LayoutDashboard,
  TrendingDown,
  PieChart,
  Trophy,
  Angry,
} from "lucide-react";
import {
  ScrollableTabs,
  type TabConfig,
} from "@/components/ui/scrollable-tabs";
import { AnalyticsTab } from "@/types/analytics";

const ANALYTICS_TABS: TabConfig<AnalyticsTab>[] = [
  { value: AnalyticsTab.OVERVIEW, label: "Overview", icon: LayoutDashboard },
  {
    value: AnalyticsTab.PREDICTIONS,
    label: "Stockout Predictions",
    icon: TrendingDown,
  },
  { value: AnalyticsTab.CATEGORIES, label: "Categories", icon: PieChart },
  {
    value: AnalyticsTab.TOP_SELLERS,
    label: "Top Sellers",
    icon: Trophy,
    adminOnly: true,
  },
  ...(process.env.NODE_ENV === "development"
    ? [{ value: AnalyticsTab.LEGACY, label: "component dump", icon: Angry } as TabConfig<AnalyticsTab>]
    : []),
];

interface AnalyticsTabsProps {
  value: AnalyticsTab;
  onValueChange: (value: AnalyticsTab) => void;
  isAdmin: boolean;
}

export function AnalyticsTabs({
  value,
  onValueChange,
  isAdmin,
}: AnalyticsTabsProps) {
  return (
    <ScrollableTabs
      tabs={ANALYTICS_TABS}
      value={value}
      onValueChange={onValueChange}
      isAdmin={isAdmin}
    />
  );
}
