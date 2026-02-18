"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { getReviewSummaries } from "@/lib/api/reviews";
import { ReviewSummary } from "@/types/api";
import { Star, Trophy, Users, TrendingUp } from "lucide-react";

const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

function StatCard({
  title,
  value,
  icon: Icon,
  isLoading,
}: {
  title: string;
  value: string | number;
  icon: React.ComponentType<{ className?: string }>;
  isLoading: boolean;
}) {
  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm text-muted-foreground">{title}</p>
            {isLoading ? (
              <Skeleton className="h-8 w-16 mt-1" />
            ) : (
              <p className="text-2xl font-bold">{value}</p>
            )}
          </div>
          <Icon className="h-8 w-8 text-muted-foreground" />
        </div>
      </CardContent>
    </Card>
  );
}

function LeaderboardTable({
  summaries,
  isLoading,
}: {
  summaries: ReviewSummary[];
  isLoading: boolean;
}) {
  if (isLoading) {
    return (
      <div className="space-y-2 p-4">
        {[...Array(5)].map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (summaries.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Star className="h-12 w-12 mb-4" />
        <p>No review data for this period</p>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-16">Rank</TableHead>
          <TableHead>Employee</TableHead>
          <TableHead className="text-right">Reviews</TableHead>
          <TableHead className="text-right">Last Review</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {summaries.map((summary, index) => (
          <TableRow key={summary.employeeId}>
            <TableCell>
              {index === 0 && (
                <Badge variant="default" className="bg-yellow-500">
                  1st
                </Badge>
              )}
              {index === 1 && (
                <Badge variant="secondary" className="bg-gray-400 text-white">
                  2nd
                </Badge>
              )}
              {index === 2 && (
                <Badge variant="secondary" className="bg-amber-700 text-white">
                  3rd
                </Badge>
              )}
              {index > 2 && (
                <span className="text-muted-foreground">{index + 1}</span>
              )}
            </TableCell>
            <TableCell className="font-medium">
              {summary.employeeName}
            </TableCell>
            <TableCell className="text-right">
              <span className="font-semibold">{summary.totalReviews}</span>
            </TableCell>
            <TableCell className="text-right text-muted-foreground">
              {summary.lastReviewDate
                ? new Date(summary.lastReviewDate).toLocaleDateString()
                : "-"}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export default function ReviewsPage() {
  const currentDate = new Date();
  const [selectedYear, setSelectedYear] = useState(currentDate.getFullYear());
  const [selectedMonth, setSelectedMonth] = useState(
    currentDate.getMonth() + 1
  );
  const [summaries, setSummaries] = useState<ReviewSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await getReviewSummaries(selectedYear, selectedMonth);
      setSummaries(data);
    } catch (error) {
      // Error handled silently
    } finally {
      setIsLoading(false);
    }
  }, [selectedYear, selectedMonth]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const stats = useMemo(() => {
    const totalReviews = summaries.reduce((sum, s) => sum + s.totalReviews, 0);
    const topPerformer = summaries[0]?.employeeName || "-";
    const employeeCount = summaries.length;
    const avgPerEmployee =
      employeeCount > 0 ? (totalReviews / employeeCount).toFixed(1) : "0";

    return { totalReviews, topPerformer, employeeCount, avgPerEmployee };
  }, [summaries]);

  // Generate year options (current year and 2 previous years)
  const yearOptions = useMemo(() => {
    const currentYear = new Date().getFullYear();
    return [currentYear, currentYear - 1, currentYear - 2];
  }, []);

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">
          Review Tracker
        </h1>
      </div>

      {/* Filters */}
      <div className="flex gap-4">
        <Select
          value={String(selectedMonth)}
          onValueChange={(v) => setSelectedMonth(Number(v))}
        >
          <SelectTrigger className="w-40">
            <SelectValue placeholder="Month" />
          </SelectTrigger>
          <SelectContent>
            {MONTHS.map((month, index) => (
              <SelectItem key={index} value={String(index + 1)}>
                {month}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={String(selectedYear)}
          onValueChange={(v) => setSelectedYear(Number(v))}
        >
          <SelectTrigger className="w-28">
            <SelectValue placeholder="Year" />
          </SelectTrigger>
          <SelectContent>
            {yearOptions.map((year) => (
              <SelectItem key={year} value={String(year)}>
                {year}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <StatCard
          title="Total Reviews"
          value={stats.totalReviews}
          icon={Star}
          isLoading={isLoading}
        />
        <StatCard
          title="Top Performer"
          value={stats.topPerformer}
          icon={Trophy}
          isLoading={isLoading}
        />
        <StatCard
          title="Employees Mentioned"
          value={stats.employeeCount}
          icon={Users}
          isLoading={isLoading}
        />
        <StatCard
          title="Avg per Employee"
          value={stats.avgPerEmployee}
          icon={TrendingUp}
          isLoading={isLoading}
        />
      </div>

      {/* Leaderboard */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-lg">
            {MONTHS[selectedMonth - 1]} {selectedYear} Leaderboard
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <LeaderboardTable summaries={summaries} isLoading={isLoading} />
        </CardContent>
      </Card>
    </div>
  );
}
