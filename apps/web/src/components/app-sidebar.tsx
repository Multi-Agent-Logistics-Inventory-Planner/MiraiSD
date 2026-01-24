"use client";

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
} from "lucide-react";

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
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/hooks/use-auth";
import { UserRole } from "@/types/api";
import { Logo } from "@/components/logo";

const mainNavItems = [
  {
    title: "Dashboard",
    href: "/",
    icon: LayoutDashboard,
  },
  {
    title: "Products",
    href: "/products",
    icon: ShoppingBag,
  },
  {
    title: "Storage",
    href: "/storage",
    icon: Warehouse,
  },
  {
    title: "Shipments",
    href: "/shipments",
    icon: Package,
  },
  {
    title: "Analytics",
    href: "/analytics",
    icon: BarChart3,
  },
  {
    title: "Alerts",
    href: "/alerts",
    icon: Bell,
  },
  {
    title: "Audit Log",
    href: "/audit-log",
    icon: ClipboardList,
  },
  {
    title: "Team",
    href: "/team",
    icon: Users,
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

export function AppSidebar() {
  const pathname = usePathname();
  const { user, isLoading, signOut } = useAuth();

  return (
    <Sidebar>
      <SidebarHeader className="p-2.5">
        <Link href="/" className="flex items-center">
          <Logo width={86} height={86} />
        </Link>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Main Menu</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {mainNavItems.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton asChild isActive={pathname === item.href}>
                    <Link href={item.href}>
                      <item.icon className="h-4 w-4" />
                      <span>{item.title}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        <SidebarGroup>
          <SidebarGroupLabel>Settings</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton asChild>
                  <Link href="/settings">
                    <Settings className="h-4 w-4" />
                    <span>Settings</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
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
                className="w-fit text-xs"
              >
                {user.role}
              </Badge>
            </div>
            <button
              onClick={signOut}
              className="text-muted-foreground hover:text-foreground"
              title="Sign out"
            >
              <LogOut className="h-4 w-4" />
            </button>
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
    </Sidebar>
  );
}
