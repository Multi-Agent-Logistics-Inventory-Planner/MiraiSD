"use client";

import {
  Bell,
  AlertTriangle,
  Info,
  Archive,
  CheckCircle,
  RotateCcw,
  Package,
  Truck,
  Monitor,
  Settings,
} from "lucide-react";
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
  onMarkAsRead?: (id: string) => void;
}

type NotificationCategory = "stock" | "shipment" | "display" | "system";

function getSeverityIcon(severity: NotificationSeverity) {
  switch (severity) {
    case NotificationSeverity.CRITICAL:
      return (
        <div className="flex items-center justify-center h-8 w-8 rounded-full bg-red-500/15">
          <AlertTriangle className="h-5 w-5 text-red-500" />
        </div>
      );
    case NotificationSeverity.WARNING:
      return (
        <div className="flex items-center justify-center h-8 w-8 rounded-full bg-amber-500/15">
          <AlertTriangle className="h-5 w-5 text-amber-500" />
        </div>
      );
    case NotificationSeverity.INFO:
      return (
        <div className="flex items-center justify-center h-8 w-8 rounded-full bg-green-500/15">
          <CheckCircle className="h-5 w-5 text-green-500" />
        </div>
      );
    default:
      return (
        <div className="flex items-center justify-center h-8 w-8 rounded-full bg-muted">
          <Bell className="h-5 w-5" />
        </div>
      );
  }
}

function getSeverityBadgeStyle(severity: NotificationSeverity) {
  switch (severity) {
    case NotificationSeverity.CRITICAL:
      return "bg-red-500/20 text-red-600 dark:text-red-400 border-red-500/30 hover:bg-red-500/30";
    case NotificationSeverity.WARNING:
      return "bg-amber-500/20 text-amber-600 dark:text-amber-400 border-amber-500/30 hover:bg-amber-500/30";
    case NotificationSeverity.INFO:
      return "bg-muted/80 text-muted-foreground border-border hover:bg-muted";
    default:
      return "bg-muted/80 text-muted-foreground border-border hover:bg-muted";
  }
}

function getCategoryBadgeStyle(category: NotificationCategory) {
  switch (category) {
    case "stock":
      return "bg-purple-500/25 text-purple-600 dark:text-purple-300 border-purple-500/40 hover:bg-purple-500/35";
    case "shipment":
      return "bg-blue-500/25 text-blue-600 dark:text-blue-300 border-blue-500/40 hover:bg-blue-500/35";
    case "display":
      return "bg-orange-500/25 text-orange-600 dark:text-orange-300 border-orange-500/40 hover:bg-orange-500/35";
    case "system":
      return "bg-emerald-500/25 text-emerald-600 dark:text-emerald-300 border-emerald-500/40 hover:bg-emerald-500/35";
    default:
      return "bg-gray-500/25 text-gray-600 dark:text-gray-300 border-gray-500/40 hover:bg-gray-500/35";
  }
}

function getTypeLabel(type: NotificationTypeEnum): string {
  switch (type) {
    case NotificationTypeEnum.OUT_OF_STOCK:
      return "Out of Stock";
    case NotificationTypeEnum.LOW_STOCK:
      return "Low Stock";
    case NotificationTypeEnum.REORDER_SUGGESTION:
      return "Reorder Suggestion";
    case NotificationTypeEnum.EXPIRY_WARNING:
      return "Expiry Warning";
    case NotificationTypeEnum.SYSTEM_ALERT:
      return "System Alert";
    case NotificationTypeEnum.SHIPMENT_COMPLETED:
      return "Shipment Completed";
    case NotificationTypeEnum.SHIPMENT_DAMAGED:
      return "Damaged Items";
    case NotificationTypeEnum.DISPLAY_STALE:
      return "Stale Display";
    default:
      return type;
  }
}

function getCategoryFromType(type: NotificationTypeEnum): NotificationCategory {
  switch (type) {
    case NotificationTypeEnum.LOW_STOCK:
    case NotificationTypeEnum.OUT_OF_STOCK:
    case NotificationTypeEnum.REORDER_SUGGESTION:
      return "stock";
    case NotificationTypeEnum.SHIPMENT_COMPLETED:
    case NotificationTypeEnum.SHIPMENT_DAMAGED:
      return "shipment";
    case NotificationTypeEnum.DISPLAY_STALE:
      return "display";
    case NotificationTypeEnum.EXPIRY_WARNING:
    case NotificationTypeEnum.SYSTEM_ALERT:
    default:
      return "system";
  }
}

function getCategoryLabel(category: NotificationCategory): string {
  switch (category) {
    case "stock":
      return "Stock";
    case "shipment":
      return "Shipment";
    case "display":
      return "Display";
    case "system":
      return "System";
  }
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  const hours = date.getHours();
  const minutes = date.getMinutes().toString().padStart(2, "0");
  const ampm = hours >= 12 ? "PM" : "AM";
  const hour12 = hours % 12 || 12;
  const month = date.toLocaleString("en-US", { month: "short" });
  const day = date.getDate();
  const year = date.getFullYear();
  return `${hour12}:${minutes}${ampm} - ${month} ${day}, ${year}`;
}

export function NotificationsTable({
  notifications,
  isLoading,
  isResolved,
  onRowClick,
  onResolve,
  onUnresolve,
  onDelete,
  onMarkAsRead,
}: NotificationsTableProps) {
  if (isLoading) {
    return (
      <div className="divide-y divide-border">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="p-6 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Skeleton className="h-8 w-8 rounded-full" />
                <Skeleton className="h-6 w-40" />
              </div>
              <Skeleton className="h-4 w-36" />
            </div>
            <div>
              <Skeleton className="h-4 w-full mb-2" />
              <Skeleton className="h-4 w-3/4" />
            </div>
            <div className="flex items-center justify-between">
              <div className="flex gap-2">
                <Skeleton className="h-6 w-16 rounded-full" />
                <Skeleton className="h-6 w-20 rounded-full" />
              </div>
              <div className="flex gap-3">
                <Skeleton className="h-8 w-24" />
                <Skeleton className="h-8 w-28" />
              </div>
            </div>
          </div>
        ))}
      </div>
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
    <div className="divide-y divide-border">
      {notifications.map((notification) => {
        const category = getCategoryFromType(notification.type);
        const isRead = !!notification.readAt;

        return (
          <div
            key={notification.id}
            className={cn(
              "p-6 cursor-pointer transition-colors hover:bg-muted/30",
              !isRead && !isResolved && "bg-muted/10"
            )}
            onClick={() => onRowClick(notification)}
          >
            {/* Header row: Icon + Title on left, Timestamp on right */}
            <div className="flex items-start justify-between gap-4 mb-2">
              <div className="flex items-center gap-3">
                {getSeverityIcon(notification.severity)}
                <h3 className="text-lg font-semibold text-foreground">
                  {getTypeLabel(notification.type)}
                </h3>
              </div>
              <span className="text-sm text-muted-foreground whitespace-nowrap">
                {formatTimestamp(notification.createdAt)}
              </span>
            </div>

            {/* Description */}
            <p className="text-[15px] text-muted-foreground mb-4 leading-relaxed">
              {notification.message}
            </p>

            {/* Footer: Tags on left, Actions on right */}
            <div className="flex items-center justify-between">
              {/* Tags */}
              <div className="flex items-center gap-2 flex-wrap">
                <Badge
                  variant="outline"
                  className={cn(
                    "text-xs font-medium rounded-full px-3 py-0.5",
                    getSeverityBadgeStyle(notification.severity)
                  )}
                >
                  {notification.severity === NotificationSeverity.INFO ? "Info" : notification.severity}
                </Badge>
                <Badge
                  variant="outline"
                  className={cn(
                    "text-xs font-medium rounded-full px-3 py-0.5",
                    getCategoryBadgeStyle(category)
                  )}
                >
                  {getCategoryLabel(category)}
                </Badge>
              </div>

              {/* Action buttons */}
              <div
                className="flex items-center gap-3"
                onClick={(e) => e.stopPropagation()}
              >
                {isResolved ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onUnresolve(notification.id)}
                    className="gap-2 text-muted-foreground hover:text-foreground"
                  >
                    <RotateCcw className="h-4 w-4" />
                    Reopen
                  </Button>
                ) : (
                  <>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onResolve(notification.id)}
                      className="gap-2 text-muted-foreground hover:text-foreground"
                    >
                      <Archive className="h-4 w-4" />
                      Archive
                    </Button>
                    {onMarkAsRead && !isRead && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onMarkAsRead(notification.id)}
                        className="gap-2 text-muted-foreground hover:text-foreground"
                      >
                        <CheckCircle className="h-4 w-4" />
                        Mark as Read
                      </Button>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
