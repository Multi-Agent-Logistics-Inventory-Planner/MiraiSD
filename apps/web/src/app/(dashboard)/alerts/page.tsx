"use client"

import { useState } from "react"
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
import { notifications, type Notification } from "@/lib/data"
import { cn } from "@/lib/utils"

function getSeverityIcon(severity: Notification["severity"]) {
  switch (severity) {
    case "critical":
      return <AlertTriangle className="h-4 w-4 text-red-500" />
    case "warning":
      return <AlertTriangle className="h-4 w-4 text-amber-500" />
    case "info":
      return <Info className="h-4 w-4 text-blue-500" />
    default:
      return <Bell className="h-4 w-4" />
  }
}

function getSeverityColor(severity: Notification["severity"]) {
  switch (severity) {
    case "critical":
      return "bg-red-100 text-red-700 border-red-200"
    case "warning":
      return "bg-amber-100 text-amber-700 border-amber-200"
    case "info":
      return "bg-blue-100 text-blue-700 border-blue-200"
    default:
      return "bg-gray-100 text-gray-700"
  }
}

function getTypeLabel(type: Notification["type"]) {
  switch (type) {
    case "stockout":
      return "Stockout"
    case "low-stock":
      return "Low Stock"
    case "shipment":
      return "Shipment"
    case "prediction":
      return "Prediction"
    case "system":
      return "System"
    default:
      return type
  }
}

export default function AlertsPage() {
  const [notificationsList, setNotificationsList] = useState(notifications)

  const totalNotifications = notificationsList.length
  const unreadCount = notificationsList.filter((n) => !n.read).length
  const criticalCount = notificationsList.filter(
    (n) => n.severity === "critical"
  ).length
  const actionRequired = notificationsList.filter(
    (n) => !n.read && (n.severity === "critical" || n.severity === "warning")
  ).length

  const markAsRead = (id: string) => {
    setNotificationsList((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    )
  }

  const markAsUnread = (id: string) => {
    setNotificationsList((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: false } : n))
    )
  }

  const deleteNotification = (id: string) => {
    setNotificationsList((prev) => prev.filter((n) => n.id !== id))
  }

  const markAllAsRead = () => {
    setNotificationsList((prev) => prev.map((n) => ({ ...n, read: true })))
  }

  return (
    <div className="flex flex-col">
      <DashboardHeader
        title="Alerts"
        description="Notifications and alerts center"
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
            <Button variant="outline" size="sm" onClick={markAllAsRead}>
              <Eye className="mr-2 h-4 w-4" />
              Mark All as Read
            </Button>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {notificationsList.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-center">
                  <Bell className="h-12 w-12 text-muted-foreground mb-4" />
                  <p className="text-muted-foreground">No notifications</p>
                </div>
              ) : (
                notificationsList.map((notification) => (
                  <div
                    key={notification.id}
                    className={cn(
                      "flex items-start gap-4 rounded-lg border p-4 transition-colors",
                      !notification.read && "bg-muted/50"
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
                          {notification.severity.charAt(0).toUpperCase() +
                            notification.severity.slice(1)}
                        </Badge>
                        <Badge variant="secondary" className="text-xs">
                          {getTypeLabel(notification.type)}
                        </Badge>
                        {notification.sku && (
                          <span className="font-mono text-xs text-muted-foreground">
                            {notification.sku}
                          </span>
                        )}
                      </div>
                      <p className="text-sm">{notification.message}</p>
                      <p className="text-xs text-muted-foreground">
                        {notification.timestamp}
                      </p>
                    </div>
                    <div className="flex items-center gap-1">
                      {notification.read ? (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => markAsUnread(notification.id)}
                          title="Mark as unread"
                        >
                          <EyeOff className="h-4 w-4" />
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => markAsRead(notification.id)}
                          title="Mark as read"
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
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
