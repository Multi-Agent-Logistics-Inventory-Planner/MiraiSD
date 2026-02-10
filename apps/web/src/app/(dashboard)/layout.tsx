import React from "react";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/app-sidebar";
import { AuthProvider } from "@/components/providers/auth-provider";
import { PermissionDeniedToast } from "@/components/auth/permission-denied-toast";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthProvider>
      <PermissionDeniedToast />
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset className="w-full min-w-0 overflow-x-hidden">
          {children}
        </SidebarInset>
      </SidebarProvider>
    </AuthProvider>
  );
}
