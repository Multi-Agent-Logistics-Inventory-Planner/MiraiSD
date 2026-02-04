"use client";

import { useState, useCallback } from "react";
import { Bell, AlertTriangle } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  NotificationsTable,
  NotificationFilters,
  NotificationPagination,
} from "@/components/notifications";
import {
  useNotificationSearch,
  useNotificationCounts,
  useResolveNotification,
  useUnresolveNotification,
  useDeleteNotification,
} from "@/hooks/queries/use-notifications";
import { DashboardHeader } from "@/components/dashboard-header";
import type { NotificationType } from "@/types/api";

const PAGE_SIZE = 20;

type NotificationTab = "active" | "resolved";

export default function NotificationsPage() {
  const [activeTab, setActiveTab] = useState<NotificationTab>("active");
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<NotificationType | undefined>();
  const [dateRange, setDateRange] = useState<{ from?: Date; to?: Date }>({});
  const [page, setPage] = useState(0);

  const countsQuery = useNotificationCounts();
  const notificationsQuery = useNotificationSearch({
    search: search || undefined,
    type: typeFilter,
    resolved: activeTab === "resolved",
    fromDate: dateRange.from?.toISOString(),
    toDate: dateRange.to?.toISOString(),
    page,
    size: PAGE_SIZE,
  });

  const resolveMutation = useResolveNotification();
  const unresolveMutation = useUnresolveNotification();
  const deleteMutation = useDeleteNotification();

  const counts = countsQuery.data ?? { active: 0, resolved: 0 };
  const paginatedData = notificationsQuery.data;
  const notifications = paginatedData?.content ?? [];

  const handleTabChange = (value: string) => {
    setActiveTab(value as NotificationTab);
    setPage(0);
  };

  const handleSearchChange = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const handleTypeChange = useCallback((type: NotificationType | undefined) => {
    setTypeFilter(type);
    setPage(0);
  }, []);

  const handleDateRangeChange = useCallback((range: { from?: Date; to?: Date }) => {
    setDateRange(range);
    setPage(0);
  }, []);

  return (
    <div className="flex flex-col">
      <DashboardHeader title="Notifications" />
      <main className="flex-1 space-y-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            {counts.active + counts.resolved} Notification{counts.active + counts.resolved !== 1 ? "s" : ""}
          </p>
        </div>

        <Tabs value={activeTab} onValueChange={handleTabChange}>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <TabsList>
                <TabsTrigger value="active">
                  Active ({counts.active})
                </TabsTrigger>
                <TabsTrigger value="resolved">
                  Resolved ({counts.resolved})
                </TabsTrigger>
              </TabsList>

              <NotificationFilters
                search={search}
                onSearchChange={handleSearchChange}
                typeFilter={typeFilter}
                onTypeFilterChange={handleTypeChange}
                dateRange={dateRange}
                onDateRangeChange={handleDateRangeChange}
              />
            </div>
          </div>
        </Tabs>

        {notificationsQuery.error ? (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-destructive" />
                Could not load notifications
              </CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {notificationsQuery.error instanceof Error
                ? notificationsQuery.error.message
                : "Unknown error"}
            </CardContent>
          </Card>
        ) : (
          <Card className="py-0">
            <CardContent className="p-0">
              <NotificationsTable
                notifications={notifications}
                isLoading={notificationsQuery.isLoading}
                isResolved={activeTab === "resolved"}
                onResolve={(id) => resolveMutation.mutate(id)}
                onUnresolve={(id) => unresolveMutation.mutate(id)}
                onDelete={(id) => deleteMutation.mutate(id)}
              />
            </CardContent>
          </Card>
        )}

        <NotificationPagination
          page={page}
          totalPages={paginatedData?.totalPages ?? 0}
          totalElements={paginatedData?.totalElements ?? 0}
          pageSize={PAGE_SIZE}
          isLoading={notificationsQuery.isLoading}
          onPageChange={setPage}
        />
      </main>
    </div>
  );
}
