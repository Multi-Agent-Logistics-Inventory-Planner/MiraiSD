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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { SidebarTrigger } from "@/components/ui/sidebar";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  getReviewSummaries,
  getReviewEmployeeById,
  updateReviewEmployee,
} from "@/lib/api/reviews";
import { ReviewSummary, ReviewEmployee } from "@/types/api";
import { usePermissions } from "@/hooks/use-permissions";
import { AddEmployeeDialog, EditEmployeeDialog } from "@/components/reviews";
import { UserPlus, Pencil, Trash2, Star, ChevronLeft, ChevronRight } from "lucide-react";

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

const ITEMS_PER_PAGE = 10;

function TableSkeleton({ isAdmin }: { isAdmin: boolean }) {
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
          {isAdmin && (
            <TableCell>
              <Skeleton className="h-8 w-16" />
            </TableCell>
          )}
        </TableRow>
      ))}
    </>
  );
}

function LeaderboardTable({
  summaries,
  isLoading,
  isAdmin,
  onEdit,
  onRemove,
  startIndex,
}: {
  summaries: ReviewSummary[];
  isLoading: boolean;
  isAdmin: boolean;
  onEdit: (summary: ReviewSummary) => void;
  onRemove: (summary: ReviewSummary) => void;
  startIndex: number;
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
      <TableHeader className="bg-muted">
        <TableRow>
          <TableHead className="w-16 rounded-tl-xl">Rank</TableHead>
          <TableHead>Employee</TableHead>
          <TableHead className={isAdmin ? "text-right" : "text-right rounded-tr-xl"}>
            Reviews
          </TableHead>
          {isAdmin && <TableHead className="w-[100px] rounded-tr-xl">Actions</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton isAdmin={isAdmin} />
        ) : (
          summaries.map((summary, index) => {
            const globalIndex = startIndex + index;
            return (
              <TableRow key={summary.employeeId}>
                <TableCell>
                  {globalIndex === 0 && (
                    <Badge variant="default" className="bg-yellow-500">
                      1st
                    </Badge>
                  )}
                  {globalIndex === 1 && (
                    <Badge variant="secondary" className="bg-gray-400 text-white">
                      2nd
                    </Badge>
                  )}
                  {globalIndex === 2 && (
                    <Badge variant="secondary" className="bg-amber-700 text-white">
                      3rd
                    </Badge>
                  )}
                  {globalIndex > 2 && (
                    <span className="text-muted-foreground">{globalIndex + 1}</span>
                  )}
                </TableCell>
                <TableCell className="font-medium">
                  {summary.employeeName}
                </TableCell>
                <TableCell className="text-right">
                  <span className="font-semibold">{summary.totalReviews}</span>
                </TableCell>
                {isAdmin && (
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => onEdit(summary)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 hover:text-destructive"
                        onClick={() => onRemove(summary)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                )}
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

  // Dialog states
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<ReviewEmployee | null>(
    null
  );
  const [deleteTarget, setDeleteTarget] = useState<ReviewSummary | null>(null);

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

  const yearOptions = useMemo(() => {
    const currentYear = new Date().getFullYear();
    return [currentYear, currentYear - 1, currentYear - 2];
  }, []);

  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const paginatedSummaries = summaries.slice(startIndex, startIndex + ITEMS_PER_PAGE);

  const handleEdit = async (summary: ReviewSummary) => {
    try {
      const employee = await getReviewEmployeeById(summary.employeeId);
      setEditingEmployee(employee);
      setIsEditDialogOpen(true);
    } catch (error) {
      // Error handled silently
    }
  };

  const handleRemove = async () => {
    if (!deleteTarget) return;
    try {
      await updateReviewEmployee(deleteTarget.employeeId, { isActive: false });
      setDeleteTarget(null);
      fetchData();
    } catch (error) {
      // Error handled silently
    }
  };

  const handleAddSuccess = () => {
    setIsAddDialogOpen(false);
    fetchData();
  };

  const handleEditSuccess = () => {
    setIsEditDialogOpen(false);
    setEditingEmployee(null);
    fetchData();
  };

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Reviews</h1>
      </div>

      {/* Filters */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex gap-2 sm:gap-4">
          <Select
            value={String(selectedMonth)}
            onValueChange={(v) => setSelectedMonth(Number(v))}
          >
            <SelectTrigger className="w-32 sm:w-40">
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
            <SelectTrigger className="w-24 sm:w-28">
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

        {isAdmin && (
          <Button onClick={() => setIsAddDialogOpen(true)} size="sm" className="sm:size-default">
            <UserPlus className="h-4 w-4 sm:mr-2" />
            <span className="hidden sm:inline">Add Employee</span>
          </Button>
        )}
      </div>

      {/* Leaderboard */}
      <Card className="py-0">
        <CardContent className="p-0">
          <LeaderboardTable
            summaries={paginatedSummaries}
            isLoading={isLoading}
            isAdmin={isAdmin}
            onEdit={handleEdit}
            onRemove={setDeleteTarget}
            startIndex={startIndex}
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

      {/* Add Employee Dialog */}
      <AddEmployeeDialog
        open={isAddDialogOpen}
        onOpenChange={setIsAddDialogOpen}
        onSuccess={handleAddSuccess}
      />

      {/* Edit Employee Dialog */}
      <EditEmployeeDialog
        employee={editingEmployee}
        open={isEditDialogOpen}
        onOpenChange={setIsEditDialogOpen}
        onSuccess={handleEditSuccess}
      />

      {/* Delete Confirmation Dialog */}
      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove Employee from Tracker</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to remove {deleteTarget?.employeeName} from
              the review tracker? They will no longer appear on the leaderboard.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleRemove}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
