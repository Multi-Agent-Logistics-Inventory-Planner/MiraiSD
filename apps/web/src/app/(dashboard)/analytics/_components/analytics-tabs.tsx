"use client";

import { Gauge, LayoutDashboard, TrendingDown } from "lucide-react";
import {
  ScrollableTabs,
  type TabConfig,
} from "@/components/ui/scrollable-tabs";
import { AnalyticsTab } from "@/types/analytics";

const ANALYTICS_TABS: TabConfig<AnalyticsTab>[] = [
  {
    value: AnalyticsTab.OVERVIEW,
    label: "Overview",
    icon: LayoutDashboard,
  },
  {
    value: AnalyticsTab.PREDICTIONS,
    label: "Forecast",
    icon: TrendingDown,
  },
  {
    value: AnalyticsTab.ACCURACY,
    label: "Accuracy",
    icon: Gauge,
  },
  // Pito (AI assistant) hidden pending redesign — restore by uncommenting.
  // {
  //   value: AnalyticsTab.ASSISTANT,
  //   label: "Pito",
  //   icon: "/pito.svg",
  //   adminOnly: true,
  // },
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
