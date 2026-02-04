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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Notification as NotificationType,
  NotificationType as NotificationTypeEnum,
  NotificationSeverity,
} from "@/types/api";
import { cn } from "@/lib/utils";

interface NotificationDetailDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  notification: NotificationType | null;
  isResolved: boolean;
  onResolve: (id: string) => void;
  onUnresolve: (id: string) => void;
  onDelete: (id: string) => void;
}

function getSeverityIcon(severity: NotificationSeverity) {
  switch (severity) {
    case NotificationSeverity.CRITICAL:
      return <AlertTriangle className="h-5 w-5 text-red-500" />;
    case NotificationSeverity.WARNING:
      return <AlertTriangle className="h-5 w-5 text-amber-500" />;
    case NotificationSeverity.INFO:
      return <Info className="h-5 w-5 text-blue-500" />;
    default:
      return <Bell className="h-5 w-5" />;
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
      return "Out of Stock";
    case NotificationTypeEnum.LOW_STOCK:
      return "Low Stock";
    case NotificationTypeEnum.REORDER_SUGGESTION:
      return "Reorder Suggestion";
    case NotificationTypeEnum.EXPIRY_WARNING:
      return "Expiry Warning";
    case NotificationTypeEnum.SYSTEM_ALERT:
      return "System Alert";
    case NotificationTypeEnum.UNASSIGNED_ITEM:
      return "Unassigned Item";
    default:
      return type;
  }
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function NotificationDetailDialog({
  open,
  onOpenChange,
  notification,
  isResolved,
  onResolve,
  onUnresolve,
  onDelete,
}: NotificationDetailDialogProps) {
  if (!notification) {
    return null;
  }

  const metadata = notification.metadata;
  const hasMetadata = metadata && Object.keys(metadata).length > 0;
  const hasRelatedIds = notification.itemId || notification.inventoryId;

  const handleDelete = () => {
    onDelete(notification.id);
    onOpenChange(false);
  };

  const handleResolve = () => {
    onResolve(notification.id);
    onOpenChange(false);
  };

  const handleUnresolve = () => {
    onUnresolve(notification.id);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-3">
            {getSeverityIcon(notification.severity)}
            <Badge
              variant="outline"
              className={cn("text-xs", getSeverityColor(notification.severity))}
            >
              {notification.severity.charAt(0) +
                notification.severity.slice(1).toLowerCase()}
            </Badge>
            <Badge variant="secondary" className="text-xs">
              {getTypeLabel(notification.type)}
            </Badge>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Full Message */}
          <div className="border rounded-lg p-4">
            <p className="text-sm leading-relaxed">{notification.message}</p>
          </div>

          {/* Details Grid */}
          <div className="border rounded-lg">
            <div className="px-4 py-3 border-b bg-muted/30">
              <h3 className="text-sm font-medium">Details</h3>
            </div>
            <div className="p-4 grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-muted-foreground text-xs mb-1">Created</p>
                <p className="font-medium">{formatDate(notification.createdAt)}</p>
              </div>
              {notification.deliveredAt && (
                <div>
                  <p className="text-muted-foreground text-xs mb-1">Read</p>
                  <p className="font-medium">{formatDate(notification.deliveredAt)}</p>
                </div>
              )}
              {notification.resolvedAt && (
                <div>
                  <p className="text-muted-foreground text-xs mb-1">Resolved</p>
                  <p className="font-medium">{formatDate(notification.resolvedAt)}</p>
                </div>
              )}
              {hasRelatedIds && (
                <>
                  {notification.itemId && (
                    <div>
                      <p className="text-muted-foreground text-xs mb-1">Item ID</p>
                      <p className="font-mono text-xs">{notification.itemId}</p>
                    </div>
                  )}
                  {notification.inventoryId && (
                    <div>
                      <p className="text-muted-foreground text-xs mb-1">Inventory ID</p>
                      <p className="font-mono text-xs">{notification.inventoryId}</p>
                    </div>
                  )}
                </>
              )}
            </div>
          </div>

          {/* Metadata */}
          {hasMetadata && (
            <div className="border rounded-lg">
              <div className="px-4 py-3 border-b bg-muted/30">
                <h3 className="text-sm font-medium">Additional Info</h3>
              </div>
              <div className="p-4 grid grid-cols-2 gap-3 text-sm">
                {Object.entries(metadata).map(([key, value]) => (
                  <div key={key}>
                    <p className="text-muted-foreground text-xs mb-1">
                      {key.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())}
                    </p>
                    <p className="font-medium text-xs">{String(value)}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Action Buttons */}
          <div className="flex items-center gap-2 pt-2">
            {isResolved ? (
              <Button variant="outline" size="sm" onClick={handleUnresolve}>
                <RotateCcw className="h-4 w-4 mr-2" />
                Reopen
              </Button>
            ) : (
              <Button variant="outline" size="sm" onClick={handleResolve}>
                <CheckCircle className="h-4 w-4 mr-2" />
                Resolve
              </Button>
            )}
            <Button variant="destructive" size="sm" onClick={handleDelete}>
              <Trash2 className="h-4 w-4 mr-2" />
              Delete
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
