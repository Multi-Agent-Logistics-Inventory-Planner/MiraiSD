"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Can, Permission } from "@/components/rbac";

interface ProductHeaderProps {
  onAddClick: () => void;
}

export function ProductHeader({ onAddClick }: ProductHeaderProps) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Products</h1>
      </div>
      <Can permission={Permission.PRODUCTS_CREATE}>
        <Button onClick={onAddClick}>
          <Plus className="mr-2 h-4 w-4" />
          Add Product
        </Button>
      </Can>
    </div>
  );
}
