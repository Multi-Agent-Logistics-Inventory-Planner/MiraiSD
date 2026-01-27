"use client";

import { SidebarTrigger } from "@/components/ui/sidebar";

export function ProductHeader() {
  return (
    <div className="flex items-center gap-2">
      <SidebarTrigger className="md:hidden" />
      <h1 className="text-2xl font-semibold tracking-tight">Products</h1>
    </div>
  );
}
