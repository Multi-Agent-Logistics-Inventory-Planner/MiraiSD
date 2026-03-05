"use client";

import type { LucideIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

interface TabPlaceholderProps {
  title: string;
  icon: LucideIcon;
}

export function TabPlaceholder({ title, icon: Icon }: TabPlaceholderProps) {
  return (
    <Card className="border-dashed">
      <CardContent className="flex flex-col items-center justify-center py-16 text-center">
        <p className="text-sm text-muted-foreground/70 mt-1">
          coming soon daddy
        </p>
      </CardContent>
    </Card>
  );
}
