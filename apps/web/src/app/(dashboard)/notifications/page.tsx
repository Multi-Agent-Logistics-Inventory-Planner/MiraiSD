"use client"

import { useState, useMemo } from "react"
import {
  Bell,
  AlertTriangle,
  Info,
  CheckCircle,
  Trash2,
  Eye,
  EyeOff,
} from "lucide-react"
import { DashboardHeader } from "@/components/dashboard-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Notification as NotificationType, NotificationType as NotificationTypeEnum, NotificationSeverity } from "@/types/api"
import { cn } from "@/lib/utils"
import {
  useNotifications,
  useMarkNotificationAsRead,
  useMarkAllNotificationsAsRead,
  useDeleteNotification,
} from "@/hooks/queries/use-notifications"

function getSeverityIcon(severity: NotificationSeverity) {
  switch (severity) {
    case NotificationSeverity.CRITICAL:
      return <AlertTriangle className="h-4 w-4 text-red-500" />
    case NotificationSeverity.WARNING:
      return <AlertTriangle className="h-4 w-4 text-amber-500" />
    case NotificationSeverity.INFO:
      return <Info className="h-4 w-4 text-blue-500" />
    default:
      return <Bell className="h-4 w-4" />
  }
}

function getSeverityColor(severity: NotificationSeverity) {
  switch (severity) {
    case NotificationSeverity.CRITICAL:
      return "bg-red-100 text-red-700 border-red-200"
    case NotificationSeverity.WARNING:
      return "bg-amber-100 text-amber-700 border-amber-200"
    case NotificationSeverity.INFO:
      return "bg-blue-100 text-blue-700 border-blue-200"
    default:
      return "bg-gray-100 text-gray-700"
  }
}

function getTypeLabel(type: NotificationTypeEnum) {
  switch (type) {
    case NotificationTypeEnum.OUT_OF_STOCK:
      return "Stockout"
    case NotificationTypeEnum.LOW_STOCK:
      return "Low Stock"
    case NotificationTypeEnum.REORDER_SUGGESTION:
      return "Prediction"
    case NotificationTypeEnum.EXPIRY_WARNING:
      return "Expiry"
    case NotificationTypeEnum.SYSTEM_ALERT:
      return "System"
    default:
      return type
  }
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return "Just now"
  if (diffMins < 60) return `${diffMins} minute${diffMins > 1 ? "s" : ""} ago`
  if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? "s" : ""} ago`
  if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? "s" : ""} ago`
  return date.toLocaleDateString()
}

export default function NotificationsPage() {
  const { data: notifications = [], isLoading, error } = useNotifications()
  const markAsReadMutation = useMarkNotificationAsRead()
  const markAllAsReadMutation = useMarkAllNotificationsAsRead()
  const deleteNotificationMutation = useDeleteNotification()

  const notificationsList = useMemo(() => {
    return notifications.sort((a, b) => {
      const dateA = new Date(a.createdAt).getTime()
      const dateB = new Date(b.createdAt).getTime()
      return dateB - dateA // Newest first
    })
  }, [notifications])

  const totalNotifications = notificationsList.length
  const unreadCount = notificationsList.filter((n) => !n.deliveredAt).length
  const criticalCount = notificationsList.filter(
    (n) => n.severity === NotificationSeverity.CRITICAL
  ).length
  const actionRequired = notificationsList.filter(
    (n) => !n.deliveredAt && (n.severity === NotificationSeverity.CRITICAL || n.severity === NotificationSeverity.WARNING)
  ).length

  const markAsRead = (id: string) => {
    markAsReadMutation.mutate(id)
  }

  const deleteNotification = (id: string) => {
    deleteNotificationMutation.mutate(id)
  }

  const markAllAsRead = () => {
    markAllAsReadMutation.mutate(undefined)
  }

  const getSkuFromMetadata = (notification: NotificationType): string | undefined => {
    return notification.metadata?.product_sku as string | undefined
  }

  return (
    <div className="flex flex-col">
      <DashboardHeader
        title="Notifications"
      />
      <main className="flex-1 space-y-6 p-4 md:p-6">
        {/* Stats Cards */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">
                Total Notifications
              </CardTitle>
              <Bell className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{totalNotifications}</div>
              <p className="text-xs text-muted-foreground">Today</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Unread</CardTitle>
              <EyeOff className="h-4 w-4 text-blue-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-blue-600">{unreadCount}</div>
              <p className="text-xs text-muted-foreground">Pending review</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Critical Alerts</CardTitle>
              <AlertTriangle className="h-4 w-4 text-red-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-red-600">{criticalCount}</div>
              <p className="text-xs text-muted-foreground">Requires immediate attention</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Action Required</CardTitle>
              <CheckCircle className="h-4 w-4 text-amber-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-amber-600">{actionRequired}</div>
              <p className="text-xs text-muted-foreground">Pending actions</p>
            </CardContent>
          </Card>
        </div>

        {/* Notifications List */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Notification Center</CardTitle>
            <Button
              variant="outline"
              size="sm"
              onClick={markAllAsRead}
              disabled={markAllAsReadMutation.isPending || unreadCount === 0}
            >
              <Eye className="mr-2 h-4 w-4" />
              Mark All as Read
            </Button>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <div key={i} className="flex items-start gap-4 rounded-lg border p-4">
                    <Skeleton className="h-4 w-4 mt-0.5" />
                    <div className="flex-1 space-y-2">
                      <Skeleton className="h-4 w-32" />
                      <Skeleton className="h-4 w-full" />
                      <Skeleton className="h-3 w-24" />
                    </div>
                  </div>
                ))
              ) : error ? (
                <div className="flex flex-col items-center justify-center py-8 text-center">
                  <AlertTriangle className="h-12 w-12 text-destructive mb-4" />
                  <p className="text-destructive font-medium">Failed to load notifications</p>
                  <p className="text-sm text-muted-foreground mt-2">
                    {error instanceof Error ? error.message : "Unknown error"}
                  </p>
                </div>
              ) : notificationsList.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-center">
                  <Bell className="h-12 w-12 text-muted-foreground mb-4" />
                  <p className="text-muted-foreground">No notifications</p>
                  <p className="text-xs text-muted-foreground mt-2">
                    Notifications will appear here when inventory alerts are triggered
                  </p>
                </div>
              ) : (
                notificationsList.map((notification) => {
                  const isRead = !!notification.deliveredAt
                  const sku = getSkuFromMetadata(notification)
                  return (
                    <div
                      key={notification.id}
                      className={cn(
                        "flex items-start gap-4 rounded-lg border p-4 transition-colors",
                        !isRead && "bg-muted/50"
                      )}
                    >
                      <div className="mt-0.5">
                        {getSeverityIcon(notification.severity)}
                      </div>
                      <div className="flex-1 space-y-1">
                        <div className="flex items-center gap-2">
                          <Badge
                            variant="outline"
                            className={cn(
                              "text-xs",
                              getSeverityColor(notification.severity)
                            )}
                          >
                            {notification.severity.charAt(0) +
                              notification.severity.slice(1).toLowerCase()}
                          </Badge>
                          <Badge variant="secondary" className="text-xs">
                            {getTypeLabel(notification.type)}
                          </Badge>
                          {sku && (
                            <span className="font-mono text-xs text-muted-foreground">
                              {sku}
                            </span>
                          )}
                        </div>
                        <p className="text-sm">{notification.message}</p>
                        <p className="text-xs text-muted-foreground">
                          {formatTimestamp(notification.createdAt)}
                        </p>
                      </div>
                      <div className="flex items-center gap-1">
                        {!isRead && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => markAsRead(notification.id)}
                            title="Mark as read"
                            disabled={markAsReadMutation.isPending}
                          >
                            <Eye className="h-4 w-4" />
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          className="text-destructive"
                          onClick={() => deleteNotification(notification.id)}
                          title="Delete notification"
                          disabled={deleteNotificationMutation.isPending}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
