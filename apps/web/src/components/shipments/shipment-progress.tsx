"use client";

import { Package, PackageCheck, Truck, CircleCheck, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";
import { CarrierStatus, ShipmentStatus } from "@/types/api";

interface ShipmentProgressProps {
  status: ShipmentStatus;
  carrierStatus?: CarrierStatus | null;
}

type ProgressStage = "completed" | "active" | "pending" | "error";

interface StageConfig {
  icon: typeof Package;
  label: string;
}

const STAGES: StageConfig[] = [
  { icon: Package, label: "Order Placed" },
  { icon: PackageCheck, label: "Packaging" },
  { icon: Truck, label: "On the Way" },
  { icon: CircleCheck, label: "Received" },
];

/**
 * Maps shipment + carrier state to progress stages.
 *
 * Stage 1 (Order Placed): always completed.
 * Stage 2 (Packaging): completed when carrier has anything beyond pre-transit (or no carrier and no receipts).
 * Stage 3 (On the Way / Truck): active = IN_TRANSIT, completed = DELIVERED, error = FAILED.
 * Stage 4 (Received): completed when status === RECEIVED, otherwise pending.
 */
function getStageStates(
  status: ShipmentStatus,
  carrierStatus: CarrierStatus | null | undefined,
): ProgressStage[] {
  // Carrier-side failure overrides the truck stage
  if (carrierStatus === CarrierStatus.FAILED) {
    return [
      "completed",
      "completed",
      "error",
      status === ShipmentStatus.RECEIVED ? "completed" : "pending",
    ];
  }

  // Stage 1 always complete
  const orderPlaced: ProgressStage = "completed";

  // Stage 2 (Packaging)
  let packaging: ProgressStage;
  if (carrierStatus === CarrierStatus.PRE_TRANSIT) {
    packaging = "active";
  } else if (
    carrierStatus === CarrierStatus.IN_TRANSIT ||
    carrierStatus === CarrierStatus.DELIVERED ||
    status === ShipmentStatus.RECEIVED
  ) {
    packaging = "completed";
  } else {
    // No carrier yet, no receipts - sit at packaging
    packaging = "active";
  }

  // Stage 3 (On the Way)
  let onTheWay: ProgressStage;
  if (carrierStatus === CarrierStatus.IN_TRANSIT) {
    onTheWay = "active";
  } else if (carrierStatus === CarrierStatus.DELIVERED || status === ShipmentStatus.RECEIVED) {
    onTheWay = "completed";
  } else {
    onTheWay = "pending";
  }

  // Stage 4 (Received)
  const received: ProgressStage = status === ShipmentStatus.RECEIVED ? "completed" : "pending";

  return [orderPlaced, packaging, onTheWay, received];
}

export function ShipmentProgress({ status, carrierStatus }: ShipmentProgressProps) {
  const stageStates = getStageStates(status, carrierStatus);

  const activeIndex = stageStates.findIndex((s) => s === "active");
  const lastCompletedIndex = stageStates.lastIndexOf("completed");
  const targetIndex = activeIndex !== -1 ? activeIndex : lastCompletedIndex;

  const progressPercent =
    targetIndex >= 0 ? (targetIndex / (STAGES.length - 1)) * 100 : 0;

  return (
    <div className="w-full py-2">
      <div className="relative flex items-center justify-between">
        <div className="absolute top-1/2 left-0 right-0 h-1 -translate-y-1/2 bg-muted rounded-full" />

        <div
          className="absolute top-1/2 left-0 h-1 -translate-y-1/2 bg-brand-primary/50 rounded-full transition-all duration-300"
          style={{ width: `${progressPercent}%` }}
        />

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
