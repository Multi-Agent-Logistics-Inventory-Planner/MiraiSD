"use client";

import { useQuery } from "@tanstack/react-query";
import { getAllUsersForReviewManagement } from "@/lib/api/reviews";
import { StatStrip } from "@/components/lootbox/admin/coins/stat-strip";
import { PlayerBalancesSection } from "@/components/lootbox/admin/coins/player-balances-section";
import { RecentActivitySection } from "@/components/lootbox/admin/coins/recent-activity-section";
import { ReviewRateFooter } from "@/components/lootbox/admin/coins/review-rate-footer";

interface CoinsTabProps {
  readonly onViewAllActivity: () => void;
}

export function CoinsTab({ onViewAllActivity }: CoinsTabProps) {
  // The grant-dialog user pool reuses the existing review-management query so we
  // don't fan out a second "list all employees" endpoint.
  const usersQuery = useQuery({
    queryKey: ["users", "all"],
    queryFn: getAllUsersForReviewManagement,
    staleTime: 60_000,
  });

  return (
    <div className="flex flex-col gap-6">
      <StatStrip />
      <ReviewRateFooter />
      <PlayerBalancesSection grantTargetUsers={usersQuery.data ?? []} />
      <RecentActivitySection onViewAll={onViewAllActivity} />
    </div>
  );
}
