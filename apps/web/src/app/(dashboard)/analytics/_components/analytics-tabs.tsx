"use client";

import {
  Activity,
  Lightbulb,
  Settings,
  TrendingDown,
  Wrench,
} from "lucide-react";
import {
  ScrollableTabs,
  type TabConfig,
} from "@/components/ui/scrollable-tabs";
import { AnalyticsTab } from "@/types/analytics";

const ANALYTICS_TABS: TabConfig<AnalyticsTab>[] = [
  {
    value: AnalyticsTab.PREDICTIONS,
    label: "Predictions",
    icon: TrendingDown,
  },
  {
    value: AnalyticsTab.INSIGHTS,
    label: "Insights",
    icon: Lightbulb,
  },
  {
    value: AnalyticsTab.DEMAND_LEADERS,
    label: "Demand Leaders",
    icon: Activity,
    adminOnly: true,
  },
  {
    value: AnalyticsTab.SETTINGS,
    label: "Settings",
    icon: Settings,
  },
  {
    value: AnalyticsTab.LEGACY,
    label: "Legacy",
    icon: Wrench,
  },
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
