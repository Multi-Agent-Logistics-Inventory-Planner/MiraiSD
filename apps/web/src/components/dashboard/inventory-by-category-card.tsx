"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  ResponsiveContainer,
  Tooltip,
  TooltipProps,
  Cell,
} from "recharts";
import { LayoutGrid } from "lucide-react";
import type { CategoryInventory } from "@/types/api";

interface InventoryByCategoryCardProps {
  data: CategoryInventory[] | undefined;
  isLoading?: boolean;
}

const CATEGORY_COLORS = [
  "#7c3aed", // violet-600
  "#0b66c2", // blue
  "#22c55e", // green-500
  "#f59e0b", // amber-500
  "#ef4444", // red-500
  "#8b5cf6", // violet-500
  "#06b6d4", // cyan-500
  "#ec4899", // pink-500
];

function CategoryTooltip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload || payload.length === 0) return null;
  const entry = payload[0].payload as CategoryInventory;
  return (
    <div className="rounded-md border bg-background px-3 py-1.5 text-xs shadow-md">
      <p className="font-medium">{entry.category}</p>
      <p className="text-muted-foreground">
        {entry.totalStock.toLocaleString()} units across {entry.totalItems} products
      </p>
    </div>
  );
}

export function InventoryByCategoryCard({
  data,
  isLoading,
}: InventoryByCategoryCardProps) {
  if (isLoading) {
    return (
      <Card className="gap-3 shadow-none">
        <CardHeader className="pb-0">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <LayoutGrid className="h-4 w-4 text-muted-foreground" />
            Inventory by Category
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-3 h-[160px]">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3">
                <Skeleton className="h-4 w-20" />
                <Skeleton className="h-4 flex-1" />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Card className="gap-3 shadow-none">
        <CardHeader className="pb-0">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <LayoutGrid className="h-4 w-4 text-muted-foreground" />
            Inventory by Category
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="h-[160px] flex items-center justify-center">
            <p className="text-sm text-muted-foreground">No category data available</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  // Sort by total stock descending and take top 5
  const sortedData = [...data]
    .sort((a, b) => b.totalStock - a.totalStock)
    .slice(0, 5);

  return (
    <Card className="gap-3 shadow-none">
      <CardHeader className="pb-0">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <LayoutGrid className="h-4 w-4 text-muted-foreground" />
          Inventory by Category
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[160px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={sortedData}
              layout="vertical"
              margin={{ top: 0, right: 0, left: 0, bottom: 0 }}
            >
              <XAxis type="number" hide />
              <YAxis
                type="category"
                dataKey="category"
                width={90}
                tick={{ fontSize: 12 }}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip content={<CategoryTooltip />} cursor={{ fill: 'transparent' }} />
              <Bar dataKey="totalStock" radius={[0, 4, 4, 0]} maxBarSize={24}>
                {sortedData.map((_, index) => (
                  <Cell
                    key={index}
                    fill={CATEGORY_COLORS[index % CATEGORY_COLORS.length]}
                    className="transition-opacity hover:opacity-80"
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}
