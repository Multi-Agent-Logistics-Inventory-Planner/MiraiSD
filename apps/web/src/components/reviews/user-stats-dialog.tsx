"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  ChevronLeft,
  ChevronRight,
  Star,
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

  const monthName = new Date(selectedYear, selectedMonth - 1).toLocaleDateString("en-US", { month: "long" });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-lg max-h-[90vh] overflow-y-auto overflow-x-hidden !px-4 sm:!px-6">
        <DialogHeader className="text-left">
          <DialogTitle className="text-lg sm:text-xl font-semibold">
            {userName || "User Stats"}
          </DialogTitle>
        </DialogHeader>

        {/* Stats */}
        {isLoadingStats ? (
          <div className="flex items-center gap-6 py-2">
            <Skeleton className="h-10 w-24" />
            <Skeleton className="h-10 w-24" />
          </div>
        ) : stats ? (
          <div className="flex items-center gap-6 py-2">
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-semibold tabular-nums">{stats.allTimeReviewCount}</span>
              <span className="text-sm text-muted-foreground">all time</span>
            </div>
            <div className="h-8 w-px bg-border" />
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-semibold tabular-nums">{stats.selectedMonthReviewCount}</span>
              <span className="text-sm text-muted-foreground">in {monthName}</span>
            </div>
          </div>
        ) : (
          <p className="text-center text-muted-foreground py-4">
            No stats available
          </p>
        )}

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
