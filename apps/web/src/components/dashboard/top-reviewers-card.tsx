"use client";

import Link from "next/link";
import { Star, Trophy, Medal, ArrowRight, Users } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useTopReviewers,
  type TopReviewer,
} from "@/hooks/queries/use-top-reviewers";

function getRankIcon(rank: number) {
  switch (rank) {
    case 1:
      return <Trophy className="h-4 w-4 text-yellow-500" />;
    case 2:
      return <Medal className="h-4 w-4 text-gray-400" />;
    case 3:
      return <Medal className="h-4 w-4 text-amber-600" />;
    default:
      return (
        <span className="text-xs font-mono text-muted-foreground w-4">
          {rank}
        </span>
      );
  }
}

interface ReviewerRowProps {
  reviewer: TopReviewer;
}

function ReviewerRow({ reviewer }: ReviewerRowProps) {
  return (
    <div className="flex items-center justify-between py-2">
      <div className="flex items-center gap-3">
        <div className="flex-shrink-0">{getRankIcon(reviewer.rank)}</div>
        <span className="font-medium text-sm">{reviewer.userName}</span>
      </div>
      <span className="text-sm font-mono font-semibold">
        {reviewer.totalReviews}
      </span>
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <Card className="gap-3">
      <CardHeader className="pb-0">
        <div className="flex items-center justify-between">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-8 w-20" />
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="bg-muted dark:bg-[#1c1c1c] rounded-xl p-4 -mx-2">
          <div className="space-y-2">
            <Skeleton className="h-8" />
            <Skeleton className="h-8" />
            <Skeleton className="h-8" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function TopReviewersCard() {
  const { data, isLoading, error } = useTopReviewers(3);

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  const hasNoData = !data || data.reviewers.length === 0;

  return (
    <Card className="gap-3 shadow-none">
      <CardHeader className="pb-0">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <Star className="h-4 w-4 text-muted-foreground" />
            Monthly Top Reviewers
            {data && (
              <span className="text-muted-foreground font-normal ml-1">
                ({data.totalReviews})
              </span>
            )}
          </CardTitle>
          <Button asChild variant="ghost" size="sm">
            <Link href="/reviews" className="gap-1">
              View All
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </Button>
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="bg-muted dark:bg-[#1c1c1c] rounded-xl p-4 -mx-2 min-h-[140px] flex flex-col justify-center">
          {error ? (
            <div className="text-center py-6 text-muted-foreground">
              <p className="text-sm">Failed to load reviewers</p>
            </div>
          ) : hasNoData ? (
            <div className="text-center text-muted-foreground">
              <Users className="h-8 w-8 mx-auto mb-2 opacity-50" />
              <p className="text-sm">No reviews this month</p>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {data.reviewers.map((reviewer) => (
                <ReviewerRow key={reviewer.userId} reviewer={reviewer} />
              ))}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
