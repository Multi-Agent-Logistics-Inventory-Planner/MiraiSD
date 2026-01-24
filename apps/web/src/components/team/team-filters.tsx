"use client";

import { Search, Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

interface TeamFiltersProps {
  searchQuery: string;
  onSearchChange: (query: string) => void;
  onInviteClick: () => void;
}

export function TeamFilters({
  searchQuery,
  onSearchChange,
  onInviteClick,
}: TeamFiltersProps) {
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="relative flex-1 max-w-sm">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search team..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-9"
        />
      </div>
      <Button onClick={onInviteClick}>
        <Mail className="mr-2 h-4 w-4" />
        Invite Employee
      </Button>
    </div>
  );
}
