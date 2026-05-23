"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { Gift } from "lucide-react";

interface PrizeCardProps {
  name: string;
  tierName: string;
  tierColor?: string | null;
  description?: string | null;
  imageUrl?: string | null;
  className?: string;
}

export function PrizeCard({
  name,
  tierName,
  tierColor,
  description,
  imageUrl,
  className,
}: PrizeCardProps) {
  return (
    <Card className={cn("overflow-hidden", className)}>
      <CardContent className="p-4 space-y-3">
        <div className="aspect-square w-full bg-muted/40 rounded-md flex items-center justify-center overflow-hidden">
          {imageUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={imageUrl}
              alt={name}
              className="w-full h-full object-cover"
            />
          ) : (
            <Gift className="h-12 w-12 text-muted-foreground" />
          )}
        </div>
        <div className="space-y-1">
          <div className="flex items-center justify-between gap-2">
            <h4 className="font-medium leading-tight truncate">{name}</h4>
            <Badge
              variant="secondary"
              style={tierColor ? { backgroundColor: tierColor, color: "white" } : undefined}
              className="shrink-0"
            >
              {tierName}
            </Badge>
          </div>
          {description ? (
            <p className="text-sm text-muted-foreground line-clamp-2">{description}</p>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
