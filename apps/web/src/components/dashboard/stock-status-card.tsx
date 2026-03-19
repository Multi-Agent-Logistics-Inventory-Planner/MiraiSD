"use client";

import { useMemo, useState } from "react";
import { useTheme } from "next-themes";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Settings2 } from "lucide-react";
import Link from "next/link";
import type { Product } from "@/types/api";

interface StockStatusCardProps {
  products: Product[];
  isLoading?: boolean;
}

interface StockStatusData {
  name: string;
  value: number;
  label: string;
  pattern: "solid" | "dots" | "diagonal" | "solid-dark";
}

function computeStockStatus(products: Product[]): StockStatusData[] {
  let inStock = 0;
  let lowStock = 0;
  let outOfStock = 0;

  for (const product of products) {
    // Skip child/prize products
    if (product.parentId) continue;

    const qty = product.quantity ?? 0;
    const reorderPoint = product.reorderPoint ?? 0;

    if (qty === 0) {
      outOfStock++;
    } else if (reorderPoint > 0 && qty <= reorderPoint) {
      lowStock++;
    } else {
      inStock++;
    }
  }

  return [
    { name: "inStock", value: inStock, label: "In stock", pattern: "solid" },
    { name: "outOfStock", value: outOfStock, label: "Out of stock", pattern: "dots" },
    { name: "lowStock", value: lowStock, label: "Low stock", pattern: "diagonal" },
  ];
}

interface LegendItemProps {
  item: StockStatusData;
}

function LegendItem({ item }: LegendItemProps) {
  const getPatternStyle = (): React.CSSProperties => {
    switch (item.pattern) {
      case "dots":
        return {
          backgroundImage: `radial-gradient(circle, currentColor 1.5px, transparent 1.5px)`,
          backgroundSize: "4px 4px",
          backgroundColor: "transparent",
        };
      case "diagonal":
        return {
          backgroundImage: `repeating-linear-gradient(
            45deg,
            currentColor,
            currentColor 1.5px,
            transparent 1.5px,
            transparent 4px
          )`,
        };
      case "solid-dark":
        return { backgroundColor: "currentColor", opacity: 0.8 };
      case "solid":
      default:
        return { backgroundColor: "currentColor" };
    }
  };

  return (
    <div className="flex items-center gap-2.5 py-1">
      <span
        className="h-3.5 w-3.5 rounded-full shrink-0 border border-border text-foreground"
        style={getPatternStyle()}
      />
      <span className="text-sm text-muted-foreground">{item.label}</span>
    </div>
  );
}

// SVG pattern definitions for the pie chart
function PatternDefs({ isDark }: { isDark?: boolean }) {
  const bgColor = isDark ? "#27272a" : "#e4e4e7"; // zinc-800 / zinc-200
  const fgColor = isDark ? "#fafafa" : "#18181b"; // zinc-50 / zinc-900

  return (
    <defs>
      <pattern id="dotsPattern" patternUnits="userSpaceOnUse" width="6" height="6">
        <rect width="6" height="6" fill={bgColor} />
        <circle cx="3" cy="3" r="1.5" fill={fgColor} />
      </pattern>
      <pattern id="diagonalPattern" patternUnits="userSpaceOnUse" width="6" height="6">
        <rect width="6" height="6" fill={bgColor} />
        <line x1="0" y1="6" x2="6" y2="0" stroke={fgColor} strokeWidth="2" />
      </pattern>
      <pattern id="solidPattern" patternUnits="userSpaceOnUse" width="4" height="4">
        <rect width="4" height="4" fill={fgColor} />
      </pattern>
    </defs>
  );
}

interface PieSliceProps {
  startAngle: number;
  endAngle: number;
  innerRadius: number;
  outerRadius: number;
  pattern: string;
  cx: number;
  cy: number;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
}

function PieSlice({ startAngle, endAngle, innerRadius, outerRadius, pattern, cx, cy, onMouseEnter, onMouseLeave }: PieSliceProps) {
  // Convert angles to radians (0 degrees = top, clockwise)
  const startRad = ((startAngle - 90) * Math.PI) / 180;
  const endRad = ((endAngle - 90) * Math.PI) / 180;

  const x1 = cx + innerRadius * Math.cos(startRad);
  const y1 = cy + innerRadius * Math.sin(startRad);
  const x2 = cx + outerRadius * Math.cos(startRad);
  const y2 = cy + outerRadius * Math.sin(startRad);
  const x3 = cx + outerRadius * Math.cos(endRad);
  const y3 = cy + outerRadius * Math.sin(endRad);
  const x4 = cx + innerRadius * Math.cos(endRad);
  const y4 = cy + innerRadius * Math.sin(endRad);

  const largeArc = endAngle - startAngle > 180 ? 1 : 0;

  const d = [
    `M ${x1} ${y1}`,
    `L ${x2} ${y2}`,
    `A ${outerRadius} ${outerRadius} 0 ${largeArc} 1 ${x3} ${y3}`,
    `L ${x4} ${y4}`,
    `A ${innerRadius} ${innerRadius} 0 ${largeArc} 0 ${x1} ${y1}`,
    "Z",
  ].join(" ");

  const getFill = () => {
    switch (pattern) {
      case "dots":
        return "url(#dotsPattern)";
      case "diagonal":
        return "url(#diagonalPattern)";
      case "solid":
      default:
        return "url(#solidPattern)";
    }
  };

  return (
    <path
      d={d}
      fill={getFill()}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      className="cursor-pointer transition-opacity hover:opacity-80"
    />
  );
}

export function StockStatusCard({ products, isLoading }: StockStatusCardProps) {
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === "dark";
  const chartData = useMemo(() => computeStockStatus(products), [products]);
  const total = chartData.reduce((sum, d) => sum + d.value, 0);
  const [hoveredSlice, setHoveredSlice] = useState<{ value: number; midAngle: number } | null>(null);

  // Calculate pie slices for semi-circle (180 degrees, from left to right)
  const slices = useMemo(() => {
    if (total === 0) return [];

    let currentAngle = 270; // Start from left, arc goes through top to right
    return chartData
      .filter((d) => d.value > 0)
      .map((item) => {
        const angle = (item.value / total) * 180; // Semi-circle = 180 degrees
        const slice = {
          ...item,
          startAngle: currentAngle,
          endAngle: currentAngle + angle,
          midAngle: currentAngle + angle / 2,
        };
        currentAngle += angle;
        return slice;
      });
  }, [chartData, total]);

  // Calculate tooltip position based on midAngle
  const getTooltipPosition = (midAngle: number) => {
    const midRad = ((midAngle - 90) * Math.PI) / 180;
    const tooltipRadius = 55; // Position tooltip at middle of the pie slice
    const cx = 120;
    const cy = 120;
    return {
      x: cx + tooltipRadius * Math.cos(midRad),
      y: cy + tooltipRadius * Math.sin(midRad),
    };
  };

  if (isLoading) {
    return (
      <Card className="relative overflow-hidden rounded-2xl p-6 shadow-none h-[240px]">
        <div className="flex items-center justify-between">
          <Skeleton className="h-6 w-16" />
          <Skeleton className="h-8 w-8 rounded-full" />
        </div>
        <div className="mt-4 flex items-center justify-between gap-4">
          <div className="flex flex-col gap-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-5 w-24" />
            ))}
          </div>
          <Skeleton className="h-[90px] w-[150px] rounded-t-full" />
        </div>
      </Card>
    );
  }

  if (total === 0) {
    return (
      <Card className="relative overflow-hidden rounded-2xl p-6 shadow-none h-[240px]">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-foreground">Stock</h3>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-muted-foreground hover:text-foreground"
          >
            <Settings2 className="h-4 w-4" />
          </Button>
        </div>
        <div className="h-[140px] flex items-center justify-center">
          <p className="text-sm text-muted-foreground">No products found</p>
        </div>
      </Card>
    );
  }

  return (
    <Card className="relative overflow-hidden rounded-2xl p-6 shadow-none h-[240px]">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-foreground">Stock</h3>
        <Link href="/products">
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-muted-foreground hover:text-foreground"
          >
            <Settings2 className="h-4 w-4" />
          </Button>
        </Link>
      </div>

      {/* Legend */}
      <div className="mt-6 flex flex-col">
        {chartData.map((item) => (
          <LegendItem key={item.name} item={item} />
        ))}
      </div>

      {/* Semi-circle pie chart */}
      <div className="absolute bottom-0 right-16">
        <svg width="180" height="120" viewBox="0 0 180 120" className="overflow-visible">
          <PatternDefs isDark={isDark} />
          <g>
            {slices.map((slice, i) => (
              <PieSlice
                key={i}
                startAngle={slice.startAngle}
                endAngle={slice.endAngle}
                innerRadius={0}
                outerRadius={110}
                pattern={slice.pattern}
                cx={120}
                cy={120}
                onMouseEnter={() => setHoveredSlice({ value: slice.value, midAngle: slice.midAngle })}
                onMouseLeave={() => setHoveredSlice(null)}
              />
            ))}
          </g>
          {/* Tooltip */}
          {hoveredSlice && (
            <g style={{ pointerEvents: "none" }}>
              <rect
                x={getTooltipPosition(hoveredSlice.midAngle).x - 24}
                y={getTooltipPosition(hoveredSlice.midAngle).y - 14}
                width="48"
                height="28"
                rx="14"
                fill={isDark ? "rgba(63, 63, 70, 0.9)" : "rgba(39, 39, 42, 0.9)"}
              />
              <text
                x={getTooltipPosition(hoveredSlice.midAngle).x}
                y={getTooltipPosition(hoveredSlice.midAngle).y + 2}
                textAnchor="middle"
                dominantBaseline="middle"
                fill="white"
                fontSize="14"
                fontWeight="600"
              >
                {hoveredSlice.value}
              </text>
            </g>
          )}
        </svg>
      </div>
    </Card>
  );
}
