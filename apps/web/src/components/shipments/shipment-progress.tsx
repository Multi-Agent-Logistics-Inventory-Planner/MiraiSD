"use client";

import { Package, PackageCheck, Truck, CircleCheck, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ShipmentStatus } from "@/types/api";

interface ShipmentProgressProps {
  status: ShipmentStatus;
  receivedQuantity: number;
  orderedQuantity: number;
}

type ProgressStage = "completed" | "active" | "pending" | "error";

interface StageConfig {
  icon: typeof Package;
  label: string;
}

// Stages aligned with shipping API flow
const STAGES: StageConfig[] = [
  { icon: Package, label: "Order Placed" },
  { icon: PackageCheck, label: "Packaging" },
  { icon: Truck, label: "On the Way" },
  { icon: CircleCheck, label: "Delivered" },
];

/**
 * Maps shipment status to progress stages:
 * - PENDING (no items received): Order placed (complete) → Packaging (active)
 * - PENDING (partial received) or IN_TRANSIT: On the Way (active)
 * - DELIVERED or fully received: All stages complete
 * - DELIVERY_FAILED: Shows error state on delivery stage
 */
function getStageStates(
  status: ShipmentStatus,
  receivedQuantity: number,
  orderedQuantity: number,
): ProgressStage[] {
  const hasPartialReceived =
    receivedQuantity > 0 && receivedQuantity < orderedQuantity;
  const isFullyReceived =
    receivedQuantity >= orderedQuantity && orderedQuantity > 0;

  // Stage 4: Delivered - all complete
  if (status === "DELIVERED" || isFullyReceived) {
    return ["completed", "completed", "completed", "completed"];
  }

  // Delivery failed - show error state on the delivery stage
  if (status === "DELIVERY_FAILED") {
    return ["completed", "completed", "completed", "error"];
  }

  // Stage 3: In Transit OR partial received - on the way is active
  // Partial receipt is used as a proxy for "on the way" when no tracking
  if (status === "IN_TRANSIT" || hasPartialReceived) {
    return ["completed", "completed", "active", "pending"];
  }

  // Stage 2: Pending with no items received - packaging is active
  if (status === "PENDING") {
    return ["completed", "active", "pending", "pending"];
  }

  // CANCELLED or unknown - only order placed is complete
  return ["completed", "pending", "pending", "pending"];
}

export function ShipmentProgress({
  status,
  receivedQuantity,
  orderedQuantity,
}: ShipmentProgressProps) {
  const stageStates = getStageStates(status, receivedQuantity, orderedQuantity);

  // Calculate progress percentage for the bar
  // The bar should extend to the active stage (or last completed stage)
  const activeIndex = stageStates.findIndex((s) => s === "active");
  const lastCompletedIndex = stageStates.lastIndexOf("completed");
  const targetIndex = activeIndex !== -1 ? activeIndex : lastCompletedIndex;

  // Calculate percentage: icons are at 0%, 33.3%, 66.6%, 100%
  const progressPercent =
    targetIndex >= 0 ? (targetIndex / (STAGES.length - 1)) * 100 : 0;

  return (
    <div className="w-full py-2">
      <div className="relative flex items-center justify-between">
        {/* Progress bar background */}
        <div className="absolute top-1/2 left-0 right-0 h-1 -translate-y-1/2 bg-muted rounded-full" />

        {/* Progress bar filled */}
        <div
          className="absolute top-1/2 left-0 h-1 -translate-y-1/2 bg-brand-primary/50 rounded-full transition-all duration-300"
          style={{ width: `${progressPercent}%` }}
        />

        {/* Stage icons */}
        {STAGES.map((stage, index) => {
          const state = stageStates[index];
          const Icon = state === "error" ? AlertTriangle : stage.icon;

          return (
            <div
              key={stage.label}
              className="relative z-10 flex flex-col items-center"
            >
              <div
                className={cn(
                  "flex h-8 w-8 items-center justify-center rounded-full border-2 transition-colors",
                  state === "completed" &&
                    "border-brand-primary bg-brand-primary text-white",
                  state === "active" &&
                    "border-brand-primary bg-background text-black dark:text-white",
                  state === "pending" &&
                    "border-muted-foreground/30 bg-background text-muted-foreground/50",
                  state === "error" &&
                    "border-red-500 bg-red-500 text-white",
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
