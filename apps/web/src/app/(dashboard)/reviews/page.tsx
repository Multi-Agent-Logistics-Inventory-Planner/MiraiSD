"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { SidebarTrigger } from "@/components/ui/sidebar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { getReviewSummaries } from "@/lib/api/reviews";
import { ReviewSummary } from "@/types/api";
import { usePermissions } from "@/hooks/use-permissions";
import { ManageEmployeesDialog, UserStatsDialog } from "@/components/reviews";
import { Settings, Star, ChevronLeft, ChevronRight, CalendarIcon, Trophy, Medal } from "lucide-react";
import { cn } from "@/lib/utils";

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

const MONTHS_SHORT = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 10 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell className="rounded-l-lg">
            <Skeleton className="h-6 w-10" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-32" />
          </TableCell>
          <TableCell className="text-right rounded-r-lg">
            <Skeleton className="h-4 w-8 ml-auto" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

function LeaderboardTable({
  summaries,
  isLoading,
  startIndex,
  onUserClick,
}: {
  summaries: ReviewSummary[];
  isLoading: boolean;
  startIndex: number;
  onUserClick: (userId: string, userName: string) => void;
}) {
  if (!isLoading && summaries.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Star className="h-12 w-12 mb-4" />
        <p>No Reviews Found</p>
      </div>
    );
  }


  return (
    <Table>
      <DataTableHeader>
        <TableHead className="w-20 rounded-l-lg">Rank</TableHead>
        <TableHead>Employee</TableHead>
        <TableHead className="text-right rounded-r-lg">Reviews</TableHead>
      </DataTableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton />
        ) : (
          summaries.map((summary, index) => {
            const globalIndex = startIndex + index;
            return (
              <TableRow
                key={summary.userId}
                className="cursor-pointer transition-colors hover:bg-muted/50"
                onClick={() => onUserClick(summary.userId, summary.userName)}
              >
                <TableCell className="rounded-l-lg">
                  <div className="flex items-center gap-2">
                    {globalIndex === 0 && (
                      <>
                        <Trophy className="h-4 w-4 text-yellow-500" />
                        <Badge variant="default" className="bg-yellow-500 hover:bg-yellow-600">
                          1st
                        </Badge>
                      </>
                    )}
                    {globalIndex === 1 && (
                      <>
                        <Medal className="h-4 w-4 text-gray-400" />
                        <Badge variant="secondary" className="bg-gray-400 text-white hover:bg-gray-500">
                          2nd
                        </Badge>
                      </>
                    )}
                    {globalIndex === 2 && (
                      <>
                        <Medal className="h-4 w-4 text-amber-700" />
                        <Badge variant="secondary" className="bg-amber-700 text-white hover:bg-amber-800">
                          3rd
                        </Badge>
                      </>
                    )}
                    {globalIndex > 2 && (
                      <span className="text-muted-foreground pl-6">{globalIndex + 1}</span>
                    )}
                  </div>
                </TableCell>
                <TableCell className="font-medium">
                  {summary.userName}
                </TableCell>
                <TableCell className="text-right rounded-r-lg">
                  <span className="font-semibold">{summary.totalReviews}</span>
                </TableCell>
              </TableRow>
            );
          })
        )}
      </TableBody>
    </Table>
  );
}

export default function ReviewsPage() {
  const { isAdmin } = usePermissions();
  const currentDate = new Date();
  const [selectedYear, setSelectedYear] = useState(currentDate.getFullYear());
  const [selectedMonth, setSelectedMonth] = useState(
    currentDate.getMonth() + 1
  );
  const [summaries, setSummaries] = useState<ReviewSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isManageDialogOpen, setIsManageDialogOpen] = useState(false);
  const [isCalendarOpen, setIsCalendarOpen] = useState(false);
  const [pickerYear, setPickerYear] = useState(currentDate.getFullYear());

  // User stats dialog state
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [selectedUserName, setSelectedUserName] = useState<string | null>(null);
  const [isStatsDialogOpen, setIsStatsDialogOpen] = useState(false);

  const handleUserClick = useCallback((userId: string, userName: string) => {
    setSelectedUserId(userId);
    setSelectedUserName(userName);
    setIsStatsDialogOpen(true);
  }, []);

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

  const formattedMonthYear = useMemo(() => {
    return `${MONTHS[selectedMonth - 1]} ${selectedYear}`;
  }, [selectedMonth, selectedYear]);

  const handleSelectMonth = useCallback((month: number) => {
    setSelectedMonth(month);
    setSelectedYear(pickerYear);
    setIsCalendarOpen(false);
  }, [pickerYear]);

  const isFutureMonth = useCallback((month: number) => {
    if (pickerYear > currentDate.getFullYear()) return true;
    if (pickerYear === currentDate.getFullYear() && month > currentDate.getMonth() + 1) return true;
    return false;
  }, [pickerYear, currentDate]);

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Reviews</h1>
      </div>

      {/* Month/Year Picker */}
      <div className="flex items-center justify-between gap-4">
        <Popover open={isCalendarOpen} onOpenChange={(open) => {
          setIsCalendarOpen(open);
          if (open) setPickerYear(selectedYear);
        }}>
          <PopoverTrigger asChild>
            <Button variant="outline" className="gap-2 dark:bg-input dark:border-[#41413d] text-muted-foreground">
              <CalendarIcon className="h-4 w-4" />
              <span>{formattedMonthYear}</span>
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-64 p-3" align="start">
            {/* Year Navigation */}
            <div className="flex items-center justify-between mb-3">
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setPickerYear((prev) => prev - 1)}
                className="h-8 w-8"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm font-medium">{pickerYear}</span>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setPickerYear((prev) => prev + 1)}
                disabled={pickerYear >= currentDate.getFullYear()}
                className="h-8 w-8"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
            {/* Month Grid */}
            <div className="grid grid-cols-3 gap-2">
              {MONTHS_SHORT.map((month, index) => {
                const monthNum = index + 1;
                const isSelected = selectedMonth === monthNum && selectedYear === pickerYear;
                const isDisabled = isFutureMonth(monthNum);
                return (
                  <Button
                    key={month}
                    variant={isSelected ? "default" : "ghost"}
                    size="sm"
                    disabled={isDisabled}
                    onClick={() => handleSelectMonth(monthNum)}
                    className={cn(
                      "h-9",
                      isSelected && "bg-primary text-primary-foreground"
                    )}
                  >
                    {month}
                  </Button>
                );
              })}
            </div>
          </PopoverContent>
        </Popover>

        {isAdmin && (
          <Button
            variant="outline"
            onClick={() => setIsManageDialogOpen(true)}
            className="text-white bg-[#0b66c2] hover:bg-[#0a5eb3] dark:bg-[#7c3aed] dark:hover:bg-[#6d28d9] dark:text-foreground"
          >
            <Settings className="h-4 w-4 mr-2" />
            Manage Employees
          </Button>
        )}
      </div>

      {/* Leaderboard */}
      <Card className="p-2 dark:border-none">
        <CardContent className="p-0">
          <LeaderboardTable
            summaries={summaries}
            isLoading={isLoading}
            startIndex={0}
            onUserClick={handleUserClick}
          />
        </CardContent>
      </Card>

      {/* Manage Employees Dialog */}
      <ManageEmployeesDialog
        open={isManageDialogOpen}
        onOpenChange={setIsManageDialogOpen}
        onSuccess={fetchData}
      />

      {/* User Stats Dialog */}
      <UserStatsDialog
        open={isStatsDialogOpen}
        onOpenChange={setIsStatsDialogOpen}
        userId={selectedUserId}
        userName={selectedUserName}
        selectedYear={selectedYear}
        selectedMonth={selectedMonth}
      />
    </div>
  );
}
