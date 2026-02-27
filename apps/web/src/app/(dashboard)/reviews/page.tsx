"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
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

const ITEMS_PER_PAGE = 10;

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 10 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell>
            <Skeleton className="h-6 w-10" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-32" />
          </TableCell>
          <TableCell className="text-right">
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

  const getRowStyles = (globalIndex: number) => {
    if (globalIndex === 0) return "bg-yellow-50/50 dark:bg-yellow-950/20 hover:bg-yellow-100/50 dark:hover:bg-yellow-950/30";
    if (globalIndex === 1) return "bg-gray-50/50 dark:bg-gray-900/20 hover:bg-gray-100/50 dark:hover:bg-gray-900/30";
    if (globalIndex === 2) return "bg-amber-50/50 dark:bg-amber-950/20 hover:bg-amber-100/50 dark:hover:bg-amber-950/30";
    return "hover:bg-muted/50";
  };

  return (
    <Table>
      <TableHeader className="bg-muted">
        <TableRow>
          <TableHead className="w-20 rounded-tl-xl">Rank</TableHead>
          <TableHead>Employee</TableHead>
          <TableHead className="text-right rounded-tr-xl">Reviews</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton />
        ) : (
          summaries.map((summary, index) => {
            const globalIndex = startIndex + index;
            return (
              <TableRow
                key={summary.userId}
                className={cn(
                  "cursor-pointer transition-colors",
                  getRowStyles(globalIndex)
                )}
                onClick={() => onUserClick(summary.userId, summary.userName)}
              >
                <TableCell>
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
                <TableCell className="text-right">
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

function LeaderboardPagination({
  page,
  pageSize,
  totalItems,
  isLoading,
  onPageChange,
}: {
  page: number;
  pageSize: number;
  totalItems: number;
  isLoading: boolean;
  onPageChange: (page: number) => void;
}) {
  const totalPages = Math.ceil(totalItems / pageSize);
  const isFirst = page === 1;
  const isLast = page >= totalPages;

  const startItem = (page - 1) * pageSize + 1;
  const endItem = Math.min(startItem + pageSize - 1, totalItems);

  if (isLoading || totalItems === 0 || totalPages <= 1) {
    return null;
  }

  return (
    <div className="flex items-center justify-between px-2 pb-4 gap-2">
      <p className="text-xs sm:text-sm text-muted-foreground whitespace-nowrap">
        Showing {startItem}-{endItem} of {totalItems}
      </p>
      <div className="flex items-center gap-1 sm:gap-2">
        <Button
          variant="outline"
          size="icon"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirst}
          className="h-8 w-8 sm:h-9 sm:w-auto sm:px-3"
        >
          <ChevronLeft className="h-4 w-4" />
          <span className="hidden sm:inline sm:ml-1">Previous</span>
        </Button>
        <span className="text-xs sm:text-sm text-muted-foreground whitespace-nowrap px-1 sm:px-2">
          Page {page} of {totalPages}
        </span>
        <Button
          variant="outline"
          size="icon"
          onClick={() => onPageChange(page + 1)}
          disabled={isLast}
          className="h-8 w-8 sm:h-9 sm:w-auto sm:px-3"
        >
          <span className="hidden sm:inline sm:mr-1">Next</span>
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
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
  const [currentPage, setCurrentPage] = useState(1);
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
      setCurrentPage(1);
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

  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const paginatedSummaries = summaries.slice(startIndex, startIndex + ITEMS_PER_PAGE);

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
            <Button variant="outline" className="gap-2">
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
            size="sm"
            className="sm:size-default"
          >
            <Settings className="h-4 w-4 sm:mr-2" />
            <span className="hidden sm:inline">Manage Employees</span>
          </Button>
        )}
      </div>

      {/* Leaderboard */}
      <Card className="py-0">
        <CardContent className="p-0">
          <LeaderboardTable
            summaries={paginatedSummaries}
            isLoading={isLoading}
            startIndex={startIndex}
            onUserClick={handleUserClick}
          />
        </CardContent>
      </Card>

      {/* Pagination */}
      <LeaderboardPagination
        page={currentPage}
        pageSize={ITEMS_PER_PAGE}
        totalItems={summaries.length}
        isLoading={isLoading}
        onPageChange={setCurrentPage}
      />

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
