"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { TeamTab } from "@/types/team";
import { TeamTabs, TabMembers, TabReviews } from "./_components";

function isValidTab(value: string | null): value is TeamTab {
  return Object.values(TeamTab).includes(value as TeamTab);
}

function TeamContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");

  const currentTab = isValidTab(tabParam) ? tabParam : TeamTab.MEMBERS;

  // Track which tabs have been visited (lazy mount - only mount on first visit)
  const [mountedTabs, setMountedTabs] = useState<Set<TeamTab>>(
    () => new Set([currentTab])
  );

  useEffect(() => {
    setMountedTabs((prev) => {
      if (prev.has(currentTab)) return prev;
      return new Set([...prev, currentTab]);
    });
  }, [currentTab]);

  const handleTabChange = (tab: TeamTab) => {
    router.push(`/team?tab=${tab}`);
  };

  return (
    <div className="flex-1 space-y-4">
      <TeamTabs value={currentTab} onValueChange={handleTabChange} />
      <div className={currentTab !== TeamTab.MEMBERS ? "hidden" : undefined}>
        {mountedTabs.has(TeamTab.MEMBERS) && <TabMembers />}
      </div>
      <div className={currentTab !== TeamTab.REVIEWS ? "hidden" : undefined}>
        {mountedTabs.has(TeamTab.REVIEWS) && <TabReviews />}
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
