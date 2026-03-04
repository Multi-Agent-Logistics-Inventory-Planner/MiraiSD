"use client";

import { Package, PackageCheck, Truck, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ShipmentStatus } from "@/types/api";

interface ShipmentProgressProps {
  status: ShipmentStatus;
  receivedQuantity: number;
  orderedQuantity: number;
}

type ProgressStage = "completed" | "active" | "pending";

interface StageConfig {
  icon: typeof Package;
  label: string;
}

const STAGES: StageConfig[] = [
  { icon: Package, label: "Order Placed" },
  { icon: PackageCheck, label: "Processing" },
  { icon: Truck, label: "Shipping" },
  { icon: CheckCircle2, label: "Delivered" },
];

function getStageStates(
  status: ShipmentStatus,
  receivedQuantity: number,
  orderedQuantity: number
): ProgressStage[] {
  const hasPartialReceived = receivedQuantity > 0 && receivedQuantity < orderedQuantity;
  const isFullyReceived = receivedQuantity >= orderedQuantity && orderedQuantity > 0;

  if (status === "DELIVERED" || isFullyReceived) {
    return ["completed", "completed", "completed", "completed"];
  }

  if (status === "IN_TRANSIT") {
    return ["completed", "completed", "active", "pending"];
  }

  if (status === "PENDING") {
    if (hasPartialReceived) {
      return ["completed", "active", "pending", "pending"];
    }
    return ["completed", "active", "pending", "pending"];
  }

  // CANCELLED or unknown
  return ["completed", "pending", "pending", "pending"];
}

export function ShipmentProgress({
  status,
  receivedQuantity,
  orderedQuantity,
}: ShipmentProgressProps) {
  const stageStates = getStageStates(status, receivedQuantity, orderedQuantity);

  // Calculate progress percentage for the bar
  const completedCount = stageStates.filter((s) => s === "completed").length;
  const hasActive = stageStates.some((s) => s === "active");
  const progressPercent = hasActive
    ? ((completedCount + 0.5) / STAGES.length) * 100
    : (completedCount / STAGES.length) * 100;

  return (
    <div className="w-full py-2">
      <div className="relative flex items-center justify-between">
        {/* Progress bar background */}
        <div className="absolute top-1/2 left-0 right-0 h-1 -translate-y-1/2 bg-muted rounded-full" />

        {/* Progress bar filled */}
        <div
          className="absolute top-1/2 left-0 h-1 -translate-y-1/2 bg-emerald-500 rounded-full transition-all duration-300"
          style={{ width: `${progressPercent}%` }}
        />

        {/* Stage icons */}
        {STAGES.map((stage, index) => {
          const state = stageStates[index];
          const Icon = stage.icon;

          return (
            <div
              key={stage.label}
              className="relative z-10 flex flex-col items-center"
            >
              <div
                className={cn(
                  "flex h-8 w-8 items-center justify-center rounded-full border-2 transition-colors",
                  state === "completed" &&
                    "border-emerald-500 bg-emerald-500 text-white",
                  state === "active" &&
                    "border-blue-500 bg-blue-500 text-white",
                  state === "pending" &&
                    "border-muted-foreground/30 bg-background text-muted-foreground/50"
                )}
              >
                <Icon className="h-4 w-4" />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
