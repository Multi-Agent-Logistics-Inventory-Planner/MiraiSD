"use client";

import { Suspense } from "react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { TeamTab } from "@/types/team";
import { useTabParam } from "@/hooks/use-tab-param";
import { TeamTabs, TabMembers, TabReviews } from "./_components";

const TEAM_TAB_VALUES = [TeamTab.MEMBERS, TeamTab.REVIEWS] as const;

function TeamContent() {
  const { value, setValue, mountedValues } = useTabParam<TeamTab>({
    values: TEAM_TAB_VALUES,
    defaultValue: TeamTab.MEMBERS,
  });
  const currentTab = value ?? TeamTab.MEMBERS;

  return (
    <div className="flex-1 space-y-4">
      <TeamTabs value={currentTab} onValueChange={setValue} />
      <div className={currentTab !== TeamTab.MEMBERS ? "hidden" : undefined}>
        {mountedValues.has(TeamTab.MEMBERS) && <TabMembers />}
      </div>
      <div className={currentTab !== TeamTab.REVIEWS ? "hidden" : undefined}>
        {mountedValues.has(TeamTab.REVIEWS) && <TabReviews />}
      </div>
    </div>
  );
}

function TeamFallback() {
  return (
    <div className="flex-1 space-y-4">
      <div className="h-9 bg-muted/30 animate-pulse rounded-md w-full max-w-xl" />
      <div className="space-y-4">
        <div className="h-12 bg-muted/30 animate-pulse rounded-lg" />
        <div className="h-96 bg-muted/30 animate-pulse rounded-lg" />
      </div>
    </div>
  );
}

export default function TeamPage() {
  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger />
        <h1 className="text-2xl font-semibold tracking-tight">Team</h1>
      </div>
      <Suspense fallback={<TeamFallback />}>
        <TeamContent />
      </Suspense>
    </div>
  );
}
