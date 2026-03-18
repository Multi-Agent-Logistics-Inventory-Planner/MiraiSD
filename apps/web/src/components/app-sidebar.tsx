"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  ShoppingBag,
  Warehouse,
  Truck,
  BarChart3,
  Bell,
  ClipboardList,
  Users,
  Settings,
  LogOut,
  ArrowUpDown,
  RefreshCw,
  Star,
  ChevronsUpDown,
  HeartCrack,
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
  useSidebar,
} from "@/components/ui/sidebar";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth } from "@/hooks/use-auth";
import { usePermissions, Permission } from "@/hooks/use-permissions";
import { useNotificationCounts } from "@/hooks/queries/use-notifications";
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
    icon: Truck,
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
    title: "Reviews",
    href: "/reviews",
    icon: Star,
    permission: Permission.REVIEWS_VIEW,
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

function NavItemLink({
  item,
  pathname,
  badge,
}: {
  item: NavItem;
  pathname: string;
  badge?: number;
}) {
  const { setOpenMobile } = useSidebar();

  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={pathname === item.href}>
        <Link href={item.href} onClick={() => setOpenMobile(false)}>
          <item.icon className="h-4 w-4 dark:text-[#faf9f5]" />
          <span className="flex-1">{item.title}</span>
          {badge !== undefined && badge > 0 && (
            <Badge className="ml-auto h-5 min-w-5 px-1.5 text-xs font-medium text-white bg-[#0b66c2]/80 dark:bg-[#7c3aed]">
              {badge > 99 ? "99+" : badge}
            </Badge>
          )}
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

function LogoLink({ href }: { href: string }) {
  const { setOpenMobile } = useSidebar();

  return (
    <Link
      href={href}
      className="flex items-center -ml-2"
      onClick={() => setOpenMobile(false)}
    >
      <Logo width={100} height={56} />
    </Link>
  );
}

function SettingsLink() {
  const { setOpenMobile } = useSidebar();

  return (
    <DropdownMenuItem asChild>
      <Link
        href="/settings"
        className="cursor-pointer"
        onClick={() => setOpenMobile(false)}
      >
        <Settings className="h-4 w-4" />
        <span>Settings</span>
      </Link>
    </DropdownMenuItem>
  );
}

function YixinLink() {
  const { setOpenMobile } = useSidebar();

  return (
    <DropdownMenuItem asChild>
      <Link
        href="/yixin"
        className="cursor-pointer"
        onClick={() => setOpenMobile(false)}
      >
        <HeartCrack className="h-4 w-4" />
        <span>Yixin</span>
      </Link>
    </DropdownMenuItem>
  );
}

export function AppSidebar() {
  const pathname = usePathname();
  const { user, isLoading, signOut } = useAuth();
  const { can } = usePermissions();
  const { data: notificationCounts } = useNotificationCounts();
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);

  const visibleInventoryItems = inventoryItems.filter((item) =>
    can(item.permission),
  );
  const visibleManagementItems = managementItems.filter((item) =>
    can(item.permission),
  );

  return (
    <Sidebar className="z-50 bg-background border-r">
      <SidebarHeader className="px-2 py-2.5">
        <LogoLink href={user?.role === UserRole.EMPLOYEE ? "/storage" : "/"} />
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
        {(can(Permission.INVENTORY_ADJUST) ||
          can(Permission.INVENTORY_TRANSFER)) && (
          <SidebarGroup>
            <SidebarGroupLabel className="font-mono uppercase text-[10px] tracking-wide">
              Quick Actions
            </SidebarGroupLabel>
            <SidebarGroupContent>
              <div className="flex gap-2 px-2">
                {can(Permission.INVENTORY_ADJUST) && (
                  <Button
                    size="sm"
                    className="flex-1 text-foreground bg-[#e4e3df] hover:bg-[#d1cfc7]/75 dark:bg-[#363633] dark:hover:bg-[#363633]"
                    onClick={() => setAdjustOpen(true)}
                  >
                    <ArrowUpDown className="h-3 w-3" />
                    <span>Adjust</span>
                  </Button>
                )}
                {can(Permission.INVENTORY_TRANSFER) && (
                  <Button
                    size="sm"
                    className="flex-1 text-foreground bg-[#e4e3df] hover:bg-[#d1cfc7]/75 dark:bg-[#363633] dark:hover:bg-[#363633]"
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
                    badge={
                      item.href === "/notifications"
                        ? notificationCounts?.unread
                        : undefined
                    }
                  />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
      </SidebarContent>
      <SidebarFooter className="border-t p-0">
        {isLoading ? (
          <div className="flex items-center gap-3 px-4 py-3">
            <Skeleton className="h-8 w-8 rounded-full" />
            <div className="flex flex-1 flex-col gap-1">
              <Skeleton className="h-3.5 w-24" />
              <Skeleton className="h-3 w-16" />
            </div>
          </div>
        ) : user ? (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="flex w-full items-center gap-3 px-4 py-3 hover:bg-accent transition-colors cursor-pointer">
                <Avatar className="h-8 w-8">
                  <AvatarFallback className="text-xs bg-[#e4e3df] dark:bg-[#363633]">
                    {getInitials(user.personName)}
                  </AvatarFallback>
                </Avatar>
                <div className="flex flex-1 flex-col text-left min-w-0">
                  <span className="text-sm font-medium truncate">
                    {user.personName || user.email}
                  </span>
                  <span className="text-[10px] text-white bg-[#0b66c2] dark:bg-[#7c3aed] dark:text-foreground px-2 py-0.5 rounded-sm w-fit">
                    {user.role}
                  </span>
                </div>
                <ChevronsUpDown className="h-4 w-4 text-muted-foreground shrink-0" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              side="top"
              align="center"
              className="w-[--radix-dropdown-menu-trigger-width] min-w-56"
            >
              <SettingsLink />
              <YixinLink />
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={signOut} className="cursor-pointer">
                <LogOut className="h-4 w-4 text-red-600 dark:text-red-400" />
                <span className="text-red-600 dark:text-red-400">Log out</span>
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        ) : (
          <div className="flex items-center gap-3 px-4 py-3">
            <Avatar className="h-8 w-8">
              <AvatarFallback className="text-xs">?</AvatarFallback>
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
