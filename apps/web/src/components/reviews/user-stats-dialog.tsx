"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  ChevronLeft,
  ChevronRight,
  Star,
  Trophy,
  Calendar,
  TrendingUp,
} from "lucide-react";
import { getUserReviewStats, getUserReviews } from "@/lib/api/reviews";
import type { UserReviewStats, Review, PaginatedResponse } from "@/types/api";

interface UserStatsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userId: string | null;
  userName: string | null;
  selectedYear: number;
  selectedMonth: number;
}

function StatCard({
  label,
  value,
  icon: Icon,
  highlight = false,
}: {
  label: string;
  value: string | number;
  icon: React.ElementType;
  highlight?: boolean;
}) {
  return (
    <Card className={highlight ? "border-yellow-500/50 bg-yellow-50/50 dark:bg-yellow-950/20" : ""}>
      <CardContent className="p-3">
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">{label}</p>
          <Icon className={`h-4 w-4 ${highlight ? "text-yellow-500" : "text-muted-foreground"}`} />
        </div>
        <p className="text-2xl font-bold mt-1">{value}</p>
      </CardContent>
    </Card>
  );
}

function StarRating({ rating }: { rating: number }) {
  return (
    <div className="flex items-center gap-0.5">
      {Array.from({ length: 5 }).map((_, i) => (
        <Star
          key={i}
          className={`h-3.5 w-3.5 ${
            i < rating
              ? "fill-yellow-400 text-yellow-400"
              : "fill-muted text-muted"
          }`}
        />
      ))}
    </div>
  );
}

function ReviewCard({ review }: { review: Review }) {
  const [expanded, setExpanded] = useState(false);
  const isLong = review.reviewText && review.reviewText.length > 150;

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  return (
    <div className="border rounded-lg p-3 space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <StarRating rating={review.rating} />
          <span className="text-xs text-muted-foreground">
            {formatDate(review.reviewDate)}
          </span>
        </div>
        <span className="text-xs text-muted-foreground">
          by {review.reviewerName || "Anonymous"}
        </span>
      </div>
      {review.reviewText && (
        <p className="text-sm">
          {expanded || !isLong
            ? review.reviewText
            : `${review.reviewText.slice(0, 150)}...`}
          {isLong && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="text-primary text-xs ml-1 hover:underline"
            >
              {expanded ? "Show less" : "Read more"}
            </button>
          )}
        </p>
      )}
    </div>
  );
}

const REVIEWS_PER_PAGE = 5;

export function UserStatsDialog({
  open,
  onOpenChange,
  userId,
  userName,
  selectedYear,
  selectedMonth,
}: UserStatsDialogProps) {
  const [stats, setStats] = useState<UserReviewStats | null>(null);
  const [reviews, setReviews] = useState<PaginatedResponse<Review> | null>(null);
  const [isLoadingStats, setIsLoadingStats] = useState(false);
  const [isLoadingReviews, setIsLoadingReviews] = useState(false);
  const [reviewPage, setReviewPage] = useState(0);

  const fetchStats = useCallback(async () => {
    if (!userId) return;
    setIsLoadingStats(true);
    try {
      const data = await getUserReviewStats(userId, selectedYear, selectedMonth);
      setStats(data);
    } catch (error) {
      console.error("Failed to fetch user stats:", error);
    } finally {
      setIsLoadingStats(false);
    }
  }, [userId, selectedYear, selectedMonth]);

  const fetchReviews = useCallback(async () => {
    if (!userId) return;
    setIsLoadingReviews(true);
    try {
      const data = await getUserReviews(userId, {
        page: reviewPage,
        size: REVIEWS_PER_PAGE,
      });
      setReviews(data);
    } catch (error) {
      console.error("Failed to fetch user reviews:", error);
    } finally {
      setIsLoadingReviews(false);
    }
  }, [userId, reviewPage]);

  useEffect(() => {
    if (open && userId) {
      fetchStats();
      setReviewPage(0);
    }
  }, [open, userId, fetchStats]);

  useEffect(() => {
    if (open && userId) {
      fetchReviews();
    }
  }, [open, userId, reviewPage, fetchReviews]);

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return "N/A";
    return new Date(dateStr).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  const getRankSuffix = (rank: number) => {
    if (rank === 1) return "st";
    if (rank === 2) return "nd";
    if (rank === 3) return "rd";
    return "th";
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-lg max-h-[90vh] overflow-y-auto overflow-x-hidden !px-4 sm:!px-6">
        <DialogHeader className="text-left">
          <DialogTitle className="text-lg sm:text-xl font-semibold flex flex-row items-center gap-2">
            {userName || "User Stats"}
            {stats && stats.allTimeRank <= 3 && (
              <Trophy className={`h-5 w-5 ${
                stats.allTimeRank === 1 ? "text-yellow-500" :
                stats.allTimeRank === 2 ? "text-gray-400" :
                "text-amber-700"
              }`} />
            )}
          </DialogTitle>
        </DialogHeader>

        {/* Stats Cards */}
        <div className="grid grid-cols-2 gap-2 mt-4">
          {isLoadingStats ? (
            <>
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
            </>
          ) : stats ? (
            <>
              <StatCard
                label="All-Time Reviews"
                value={stats.allTimeReviewCount}
                icon={Star}
                highlight
              />
              <StatCard
                label="All-Time Rank"
                value={`${stats.allTimeRank}${getRankSuffix(stats.allTimeRank)}`}
                icon={Trophy}
                highlight={stats.allTimeRank <= 3}
              />
              <StatCard
                label="This Month"
                value={stats.selectedMonthReviewCount}
                icon={TrendingUp}
              />
              <StatCard
                label="First Review"
                value={stats.firstReviewDate ? formatDate(stats.firstReviewDate) : "N/A"}
                icon={Calendar}
              />
            </>
          ) : (
            <p className="col-span-2 text-center text-muted-foreground py-4">
              No stats available
            </p>
          )}
        </div>

        {/* Reviews Section */}
        <div className="mt-6">
          <h3 className="text-sm font-medium mb-3">Recent Reviews</h3>

          {isLoadingReviews ? (
            <div className="space-y-2">
              <Skeleton className="h-24" />
              <Skeleton className="h-24" />
              <Skeleton className="h-24" />
            </div>
          ) : reviews && reviews.content.length > 0 ? (
            <>
              <div className="space-y-2">
                {reviews.content.map((review) => (
                  <ReviewCard key={review.id} review={review} />
                ))}
              </div>

              {/* Pagination */}
              {reviews.totalPages > 1 && (
                <div className="flex items-center justify-between mt-4 pt-2 border-t">
                  <p className="text-xs text-muted-foreground">
                    Page {reviews.number + 1} of {reviews.totalPages}
                  </p>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => setReviewPage((p) => Math.max(0, p - 1))}
                      disabled={reviews.first}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => setReviewPage((p) => p + 1)}
                      disabled={reviews.last}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
              <Star className="h-8 w-8 mb-2" />
              <p className="text-sm">No reviews found</p>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
