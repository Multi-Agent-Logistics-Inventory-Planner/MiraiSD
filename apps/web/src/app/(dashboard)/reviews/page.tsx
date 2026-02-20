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

function LeaderboardTable({
  summaries,
  isLoading,
  isAdmin,
  onEdit,
  onRemove,
  currentPage,
  onPageChange,
}: {
  summaries: ReviewSummary[];
  isLoading: boolean;
  isAdmin: boolean;
  onEdit: (summary: ReviewSummary) => void;
  onRemove: (summary: ReviewSummary) => void;
  currentPage: number;
  onPageChange: (page: number) => void;
}) {
  const totalPages = Math.ceil(summaries.length / ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const paginatedSummaries = summaries.slice(startIndex, startIndex + ITEMS_PER_PAGE);

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
        <p>No employees in the review tracker</p>
      </div>
    );
  }

  return (
    <div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-16">Rank</TableHead>
            <TableHead>Employee</TableHead>
            <TableHead className="text-right">Reviews</TableHead>
            {isAdmin && <TableHead className="w-[100px]">Actions</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {paginatedSummaries.map((summary, index) => {
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
          })}
        </TableBody>
      </Table>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-3 border-t">
          <p className="text-sm text-muted-foreground">
            Showing {startIndex + 1}-{Math.min(startIndex + ITEMS_PER_PAGE, summaries.length)} of {summaries.length}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => onPageChange(currentPage - 1)}
              disabled={currentPage === 1}
            >
              <ChevronLeft className="h-4 w-4" />
              Previous
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {currentPage} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => onPageChange(currentPage + 1)}
              disabled={currentPage === totalPages}
            >
              Next
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
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
      setCurrentPage(1); // Reset to first page when data changes
    } catch (error) {
      // Error handled silently
    } finally {
      setIsLoading(false);
    }
  }, [selectedYear, selectedMonth]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Generate year options (current year and 2 previous years)
  const yearOptions = useMemo(() => {
    const currentYear = new Date().getFullYear();
    return [currentYear, currentYear - 1, currentYear - 2];
  }, []);

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

        {isAdmin && (
          <Button onClick={() => setIsAddDialogOpen(true)}>
            <UserPlus className="mr-2 h-4 w-4" />
            Add Employee
          </Button>
        )}
      </div>

      {/* Leaderboard */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-lg">
            {MONTHS[selectedMonth - 1]} {selectedYear} Leaderboard
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <LeaderboardTable
            summaries={summaries}
            isLoading={isLoading}
            isAdmin={isAdmin}
            onEdit={handleEdit}
            onRemove={setDeleteTarget}
            currentPage={currentPage}
            onPageChange={setCurrentPage}
          />
        </CardContent>
      </Card>

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
