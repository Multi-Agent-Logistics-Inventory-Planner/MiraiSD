"use client";

import { Users, Star, Gift } from "lucide-react";
import {
  ScrollableTabs,
  type TabConfig,
} from "@/components/ui/scrollable-tabs";
import { TeamTab } from "@/types/team";

const TEAM_TABS: TabConfig<TeamTab>[] = [
  {
    value: TeamTab.MEMBERS,
    label: "Members",
    icon: Users,
  },
  {
    value: TeamTab.REVIEWS,
    label: "Reviews",
    icon: Star,
  },
  {
    value: TeamTab.LOOTBOX,
    label: "Box",
    icon: Gift,
  },
];

interface TeamTabsProps {
  value: TeamTab;
  onValueChange: (value: TeamTab) => void;
}

export function TeamTabs({ value, onValueChange }: TeamTabsProps) {
  return (
    <ScrollableTabs
      tabs={TEAM_TABS}
      value={value}
      onValueChange={onValueChange}
      isAdmin={true}
    />
  );
}
