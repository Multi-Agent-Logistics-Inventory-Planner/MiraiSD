"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  ShoppingBag,
  Warehouse,
  Package,
  BarChart3,
  Bell,
  ClipboardList,
  Users,
  Settings,
  LogOut,
  MoreVertical,
  ArrowUpDown,
  RefreshCw,
} from "lucide-react";

import { AdjustStockDialog } from "@/components/stock/adjust-stock-dialog";
import { TransferStockDialog } from "@/components/stock/transfer-stock-dialog";

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/hooks/use-auth";
import { usePermissions, Permission } from "@/hooks/use-permissions";
import { UserRole } from "@/types/api";
import { Logo } from "@/components/logo";
import type { PermissionKey } from "@/lib/rbac";

interface NavItem {
  title: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  permission: PermissionKey;
}

const dashboardItem: NavItem = {
  title: "Dashboard",
  href: "/",
  icon: LayoutDashboard,
  permission: Permission.DASHBOARD_VIEW,
};

const inventoryItems: NavItem[] = [
  {
    title: "Storage",
    href: "/storage",
    icon: Warehouse,
    permission: Permission.STORAGE_VIEW,
  },
  {
    title: "Products",
    href: "/products",
    icon: ShoppingBag,
    permission: Permission.PRODUCTS_VIEW,
  },
  {
    title: "Shipments",
    href: "/shipments",
    icon: Package,
    permission: Permission.SHIPMENTS_VIEW,
  },
];

const managementItems: NavItem[] = [
  {
    title: "Analytics",
    href: "/analytics",
    icon: BarChart3,
    permission: Permission.ANALYTICS_VIEW,
  },
  {
    title: "Audit Log",
    href: "/audit-log",
    icon: ClipboardList,
    permission: Permission.AUDIT_LOG_VIEW,
  },
  {
    title: "Notifications",
    href: "/notifications",
    icon: Bell,
    permission: Permission.NOTIFICATIONS_VIEW,
  },
  {
    title: "Team",
    href: "/team",
    icon: Users,
    permission: Permission.TEAM_VIEW,
  },
];

function getInitials(name: string | undefined): string {
  if (!name) return "U";
  const parts = name.split(" ");
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}

function getRoleBadgeVariant(role: UserRole): "default" | "secondary" {
  return role === UserRole.ADMIN ? "default" : "secondary";
}

function NavItemLink({ item, pathname }: { item: NavItem; pathname: string }) {
  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={pathname === item.href}>
        <Link href={item.href}>
          <item.icon className="h-4 w-4" />
          <span>{item.title}</span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

export function AppSidebar() {
  const pathname = usePathname();
  const { user, isLoading, signOut } = useAuth();
  const { can } = usePermissions();
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);

  const visibleInventoryItems = inventoryItems.filter((item) =>
    can(item.permission),
  );
  const visibleManagementItems = managementItems.filter((item) =>
    can(item.permission),
  );

  return (
    <Sidebar>
      <SidebarHeader className="p-2.5">
        <Link href="/" className="flex items-center">
          <Logo width={86} height={86} />
        </Link>
      </SidebarHeader>
      <SidebarContent>
        {can(dashboardItem.permission) && (
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                <NavItemLink item={dashboardItem} pathname={pathname} />
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
        {(can(Permission.INVENTORY_ADJUST) || can(Permission.INVENTORY_TRANSFER)) && (
          <SidebarGroup>
            <SidebarGroupLabel className="font-mono uppercase text-[10px] tracking-wide">
              Quick Actions
            </SidebarGroupLabel>
            <SidebarGroupContent>
              <div className="flex gap-2 px-2">
                {can(Permission.INVENTORY_ADJUST) && (
                  <Button
                    size="sm"
                    className="flex-1"
                    onClick={() => setAdjustOpen(true)}
                  >
                    <ArrowUpDown className="h-3 w-3" />
                    <span>Adjust</span>
                  </Button>
                )}
                {can(Permission.INVENTORY_TRANSFER) && (
                  <Button
                    size="sm"
                    className="flex-1"
                    onClick={() => setTransferOpen(true)}
                  >
                    <RefreshCw className="h-3 w-3" />
                    <span>Transfer</span>
                  </Button>
                )}
              </div>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
        {visibleInventoryItems.length > 0 && (
          <SidebarGroup>
            <SidebarGroupLabel className="font-mono uppercase text-[10px] tracking-wide">
              Inventory
            </SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {visibleInventoryItems.map((item) => (
                  <NavItemLink
                    key={item.href}
                    item={item}
                    pathname={pathname}
                  />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
        {visibleManagementItems.length > 0 && (
          <SidebarGroup>
            <SidebarGroupLabel className="font-mono uppercase text-[10px] tracking-wide">
              Management
            </SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {visibleManagementItems.map((item) => (
                  <NavItemLink
                    key={item.href}
                    item={item}
                    pathname={pathname}
                  />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
      </SidebarContent>
      <SidebarFooter className="p-4">
        {isLoading ? (
          <div className="flex items-center gap-3">
            <Skeleton className="h-9 w-9 rounded-full" />
            <div className="flex flex-1 flex-col gap-1">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-3 w-16" />
            </div>
          </div>
        ) : user ? (
          <div className="flex items-center gap-3">
            <Avatar className="h-9 w-9">
              <AvatarFallback>{getInitials(user.personName)}</AvatarFallback>
            </Avatar>
            <div className="flex flex-1 flex-col">
              <span className="text-sm font-medium">
                {user.personName || user.email}
              </span>
              <Badge
                variant={getRoleBadgeVariant(user.role)}
                className="w-fit text-[10px]"
              >
                {user.role}
              </Badge>
            </div>
            <Popover>
              <PopoverTrigger asChild>
                <button
                  className="text-muted-foreground hover:text-foreground cursor-pointer"
                  title="More options"
                  aria-label="More options"
                >
                  <MoreVertical className="h-4 w-4" />
                </button>
              </PopoverTrigger>
              <PopoverContent align="end" side="top" className="w-48 p-1">
                <div className="flex flex-col">
                  <Link
                    href="/settings"
                    className="flex items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent"
                  >
                    <Settings className="h-4 w-4" />
                    <span>Settings</span>
                  </Link>
                  <button
                    onClick={signOut}
                    className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm text-destructive hover:bg-accent"
                  >
                    <LogOut className="h-4 w-4" />
                    <span>Log out</span>
                  </button>
                </div>
              </PopoverContent>
            </Popover>
          </div>
        ) : (
          <div className="flex items-center gap-3">
            <Avatar className="h-9 w-9">
              <AvatarFallback>?</AvatarFallback>
            </Avatar>
            <div className="flex flex-1 flex-col">
              <span className="text-sm font-medium text-muted-foreground">
                Not signed in
              </span>
            </div>
          </div>
        )}
      </SidebarFooter>

      <AdjustStockDialog open={adjustOpen} onOpenChange={setAdjustOpen} />
      <TransferStockDialog open={transferOpen} onOpenChange={setTransferOpen} />
    </Sidebar>
  );
}
