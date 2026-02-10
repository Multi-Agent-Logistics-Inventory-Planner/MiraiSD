"use client";

import {
  Bell,
  AlertTriangle,
  Info,
  Trash2,
  CheckCircle,
  RotateCcw,
} from "lucide-react";
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
import {
  Notification as NotificationType,
  NotificationType as NotificationTypeEnum,
  NotificationSeverity,
} from "@/types/api";
import { cn } from "@/lib/utils";

interface NotificationsTableProps {
  notifications: NotificationType[];
  isLoading: boolean;
  isResolved: boolean;
  onRowClick: (notification: NotificationType) => void;
  onResolve: (id: string) => void;
  onUnresolve: (id: string) => void;
  onDelete: (id: string) => void;
}

function getSeverityIcon(severity: NotificationSeverity) {
  switch (severity) {
    case NotificationSeverity.CRITICAL:
      return <AlertTriangle className="h-4 w-4 text-red-500" />;
    case NotificationSeverity.WARNING:
      return <AlertTriangle className="h-4 w-4 text-amber-500" />;
    case NotificationSeverity.INFO:
      return <Info className="h-4 w-4 text-blue-500" />;
    default:
      return <Bell className="h-4 w-4" />;
  }
}

function getSeverityColor(severity: NotificationSeverity) {
  switch (severity) {
    case NotificationSeverity.CRITICAL:
      return "bg-red-100 text-red-700 border-red-200";
    case NotificationSeverity.WARNING:
      return "bg-amber-100 text-amber-700 border-amber-200";
    case NotificationSeverity.INFO:
      return "bg-blue-100 text-blue-700 border-blue-200";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

function getTypeLabel(type: NotificationTypeEnum) {
  switch (type) {
    case NotificationTypeEnum.OUT_OF_STOCK:
      return "Stockout";
    case NotificationTypeEnum.LOW_STOCK:
      return "Low Stock";
    case NotificationTypeEnum.REORDER_SUGGESTION:
      return "Reorder";
    case NotificationTypeEnum.EXPIRY_WARNING:
      return "Expiry";
    case NotificationTypeEnum.SYSTEM_ALERT:
      return "System";
    case NotificationTypeEnum.UNASSIGNED_ITEM:
      return "Unassigned";
    default:
      return type;
  }
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return "Just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function NotificationsTable({
  notifications,
  isLoading,
  isResolved,
  onRowClick,
  onResolve,
  onUnresolve,
  onDelete,
}: NotificationsTableProps) {
  if (isLoading) {
    return (
      <>
        {/* Mobile loading skeleton */}
        <div className="sm:hidden divide-y">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="p-4 space-y-2">
              <div className="flex items-center gap-2">
                <Skeleton className="h-4 w-4" />
                <Skeleton className="h-5 w-16" />
                <Skeleton className="h-4 w-12" />
              </div>
              <Skeleton className="h-4 w-full" />
            </div>
          ))}
        </div>

        {/* Desktop loading skeleton */}
        <Table className="hidden sm:table">
          <TableHeader className="bg-muted">
            <TableRow>
              <TableHead className="w-[50px] rounded-tl-lg" />
              <TableHead>Type</TableHead>
              <TableHead className="w-full">Content</TableHead>
              <TableHead>Date</TableHead>
              <TableHead className="w-[100px] rounded-tr-lg" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                <TableCell>
                  <Skeleton className="h-4 w-4" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-5 w-16" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-full max-w-md" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-20" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-8 w-16" />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </>
    );
  }

  if (notifications.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Bell className="h-12 w-12 text-muted-foreground/50 mb-4" />
        <p className="text-muted-foreground">No notifications found</p>
      </div>
    );
  }

  return (
    <>
      {/* Mobile card layout */}
      <div className="sm:hidden divide-y">
        {notifications.map((notification) => (
          <div
            key={notification.id}
            className="p-4 cursor-pointer hover:bg-muted/50 active:bg-muted"
            onClick={() => onRowClick(notification)}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2 flex-1 min-w-0">
                {getSeverityIcon(notification.severity)}
                <Badge
                  variant="outline"
                  className={cn(
                    "text-xs whitespace-nowrap",
                    getSeverityColor(notification.severity)
                  )}
                >
                  {getTypeLabel(notification.type)}
                </Badge>
                <span className="text-xs text-muted-foreground">
                  {formatTimestamp(notification.createdAt)}
                </span>
              </div>
              <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                {isResolved ? (
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => onUnresolve(notification.id)}
                    title="Reopen notification"
                  >
                    <RotateCcw className="h-4 w-4" />
                  </Button>
                ) : (
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => onResolve(notification.id)}
                    title="Resolve notification"
                  >
                    <CheckCircle className="h-4 w-4" />
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="text-destructive"
                  onClick={() => onDelete(notification.id)}
                  title="Delete notification"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </div>
            <p className="text-sm mt-2 text-foreground">
              {notification.message}
            </p>
          </div>
        ))}
      </div>

      {/* Desktop table layout */}
      <Table className="hidden sm:table">
        <TableHeader className="bg-muted">
          <TableRow>
            <TableHead className="w-[50px] rounded-tl-lg" />
            <TableHead>Type</TableHead>
            <TableHead className="w-full">Content</TableHead>
            <TableHead>Date</TableHead>
            <TableHead className="w-[100px] rounded-tr-lg" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {notifications.map((notification) => (
            <TableRow
              key={notification.id}
              className="cursor-pointer hover:bg-muted/50"
              onClick={() => onRowClick(notification)}
            >
              <TableCell>{getSeverityIcon(notification.severity)}</TableCell>
              <TableCell>
                <Badge
                  variant="outline"
                  className={cn(
                    "text-xs whitespace-nowrap",
                    getSeverityColor(notification.severity)
                  )}
                >
                  {getTypeLabel(notification.type)}
                </Badge>
              </TableCell>
              <TableCell>
                <p className="text-sm truncate max-w-md" title={notification.message}>
                  {notification.message}
                </p>
              </TableCell>
              <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                {formatTimestamp(notification.createdAt)}
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                  {isResolved ? (
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => onUnresolve(notification.id)}
                      title="Reopen notification"
                    >
                      <RotateCcw className="h-4 w-4" />
                    </Button>
                  ) : (
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => onResolve(notification.id)}
                      title="Resolve notification"
                    >
                      <CheckCircle className="h-4 w-4" />
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="text-destructive"
                    onClick={() => onDelete(notification.id)}
                    title="Delete notification"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}
