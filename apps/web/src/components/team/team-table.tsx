"use client";

import { useState } from "react";
import { Pencil, Trash2, RefreshCw } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
import { cn } from "@/lib/utils";

export type TeamMemberRow = {
  id: string;
  fullName: string;
  email: string;
  role: string;
  status: "active" | "pending";
  lastAudit: string | null;
  createdAt: string;
  type: "member" | "invitation";
};

function getStatusColor(status: "active" | "pending") {
  switch (status) {
    case "active":
      return "bg-green-100 text-green-700";
    case "pending":
      return "bg-amber-100 text-amber-700";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

function getRoleColor(role: "admin" | "employee") {
  switch (role) {
    case "admin":
      return "bg-purple-100 text-purple-700";
    case "employee":
      return "bg-gray-100 text-gray-700";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 6 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-36" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-6 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-6 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

interface TeamTableProps {
  data: TeamMemberRow[];
  isLoading: boolean;
  onEdit: (row: TeamMemberRow) => void;
  onDelete: (row: TeamMemberRow) => void;
  onResendInvite: (email: string) => void;
}

export function TeamTable({
  data,
  isLoading,
  onEdit,
  onDelete,
  onResendInvite,
}: TeamTableProps) {
  const [deleteTarget, setDeleteTarget] = useState<TeamMemberRow | null>(null);

  const handleDeleteConfirm = () => {
    if (deleteTarget) {
      onDelete(deleteTarget);
      setDeleteTarget(null);
    }
  };

  return (
    <>
    <Table>
      <TableHeader className="bg-muted">
        <TableRow>
          <TableHead className="rounded-tl-xl">Name</TableHead>
          <TableHead>Email</TableHead>
          <TableHead className="text-center">Role</TableHead>
          <TableHead className="text-center">Status</TableHead>
          <TableHead className="text-center">Last Audit</TableHead>
          <TableHead className="w-[120px] rounded-tr-xl">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton />
        ) : data.length === 0 ? (
          <TableRow>
            <TableCell
              colSpan={6}
              className="h-24 text-center text-muted-foreground"
            >
              No team members found.
            </TableCell>
          </TableRow>
        ) : (
          data.map((row) => (
            <TableRow key={row.id}>
              <TableCell className="font-medium">{row.fullName}</TableCell>
              <TableCell>{row.email}</TableCell>
              <TableCell className="text-center">
                <Badge
                  variant="outline"
                  className={cn(
                    "text-xs",
                    getRoleColor(row.role.toLowerCase() as "admin" | "employee")
                  )}
                >
                  {row.role.charAt(0).toUpperCase() +
                    row.role.slice(1).toLowerCase()}
                </Badge>
              </TableCell>
              <TableCell className="text-center">
                <Badge
                  variant="outline"
                  className={cn("text-xs", getStatusColor(row.status))}
                >
                  {row.status.charAt(0).toUpperCase() + row.status.slice(1)}
                </Badge>
              </TableCell>
              <TableCell className="text-center text-muted-foreground">
                {row.lastAudit
                  ? new Date(row.lastAudit).toLocaleDateString()
                  : "-"}
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  {row.status === "pending" && (
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      title="Resend invitation"
                      onClick={() => onResendInvite(row.email)}
                    >
                      <RefreshCw className="h-4 w-4" />
                    </Button>
                  )}
                  {row.type === "member" && (
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => onEdit(row)}
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="text-destructive"
                    onClick={() => setDeleteTarget(row)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>

    <AlertDialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {deleteTarget?.type === "member" ? "Remove Team Member" : "Cancel Invitation"}
          </AlertDialogTitle>
          <AlertDialogDescription>
            {deleteTarget?.type === "member"
              ? `Are you sure you want to remove ${deleteTarget?.fullName}? This action cannot be undone.`
              : `Are you sure you want to cancel the invitation for ${deleteTarget?.email}?`}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleDeleteConfirm}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {deleteTarget?.type === "member" ? "Remove" : "Cancel Invitation"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
    </>
  );
}
