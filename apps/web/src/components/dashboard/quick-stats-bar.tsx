"use client";

import Link from "next/link";
import { Package, Bell, Truck } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { QuickStats } from "@/types/dashboard";

interface QuickStatsBarProps {
  stats: QuickStats | null;
  isLoading?: boolean;
}

interface StatItemProps {
  icon: React.ReactNode;
  label: string;
  value: string | number | React.ReactNode;
  href?: string;
  highlight?: boolean;
}

function StatItem({ icon, label, value, href, highlight }: StatItemProps) {
  const content = (
    <div
      className={cn(
        "flex items-center gap-2 px-4 py-2 rounded-md transition-colors",
        href && "hover:bg-accent cursor-pointer",
        highlight && "bg-red-50 dark:bg-red-900/20"
      )}
    >
      <span className={cn("text-muted-foreground", highlight && "text-red-500")}>{icon}</span>
      <div className="flex flex-col min-w-0">
        <span className="text-xs text-muted-foreground truncate">{label}</span>
        <span
          className={cn(
            "text-sm font-medium truncate",
            highlight && "text-red-600 dark:text-red-400"
          )}
        >
          {value}
        </span>
      </div>
    </div>
  );

  if (href) {
    return <Link href={href}>{content}</Link>;
  }

  return content;
}

function AlertSeverityBadges({ severity }: { severity: { critical: number; warning: number; info: number } }) {
  const total = severity.critical + severity.warning + severity.info;

  if (total === 0) {
    return <span>0</span>;
  }

  return (
    <div className="flex items-center gap-1">
      {severity.critical > 0 && (
        <Badge variant="destructive" className="h-5 px-1.5 text-xs font-mono">
          {severity.critical}
        </Badge>
      )}
      {severity.warning > 0 && (
        <Badge className="h-5 px-1.5 text-xs font-mono bg-amber-500 hover:bg-amber-500 text-white">
          {severity.warning}
        </Badge>
      )}
      {severity.info > 0 && (
        <Badge variant="secondary" className="h-5 px-1.5 text-xs font-mono">
          {severity.info}
        </Badge>
      )}
    </div>
  );
}

export function QuickStatsBar({ stats, isLoading }: QuickStatsBarProps) {
  if (isLoading) {
    return (
      <div className="flex items-center justify-between gap-2 px-4 py-2 border-t bg-muted/30">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center gap-2 px-4">
            <Skeleton className="h-4 w-4" />
            <div className="flex flex-col gap-1">
              <Skeleton className="h-3 w-16" />
              <Skeleton className="h-4 w-12" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (!stats) {
    return null;
  }

  const hasAlerts = stats.activeAlerts > 0;

  return (
    <div className="flex flex-wrap items-center justify-between gap-2 px-2 py-1 border-t bg-muted/30">
      <StatItem
        icon={<Package className="h-4 w-4" />}
        label="Total Products"
        value={stats.totalSkus}
      />
      <StatItem
        icon={<Bell className="h-4 w-4" />}
        label="Active Alerts"
        value={<AlertSeverityBadges severity={stats.alertsBySeverity} />}
        href="/notifications"
        highlight={hasAlerts}
      />
      <StatItem
        icon={<Truck className="h-4 w-4" />}
        label="Pending Shipments"
        value={stats.pendingShipments}
        href="/shipments"
        highlight={stats.pendingShipments > 0}
      />
    </div>
  );
}
