"use client";

import { Skeleton } from "@/components/ui/skeleton";
import {
  REEL_DIM_DESKTOP,
  REEL_DIM_MOBILE,
} from "@/components/lootbox/reel/use-reel";

interface HeroZoneSkeletonProps {
  readonly isDesktop: boolean;
}

export function HeroZoneSkeleton({ isDesktop }: HeroZoneSkeletonProps) {
  const dim = isDesktop ? REEL_DIM_DESKTOP : REEL_DIM_MOBILE;

  return (
    <div
      className="relative overflow-hidden rounded-2xl border border-border px-6 pb-8 pt-10 sm:px-10"
      style={{
        background:
          "radial-gradient(ellipse at 50% 30%, rgba(139,92,246,0.10), transparent 60%), var(--table-header)",
      }}
    >
      <div className="mt-2 flex flex-col items-center gap-3.5">
        <Skeleton className="h-7 w-56" />
        <Skeleton className="h-3 w-64" />
        <Skeleton className="mt-1 h-[140px] w-[140px] rounded-2xl" />
      </div>

      <div className="mx-auto mt-8 w-full max-w-[924px]">
        <Skeleton
          className="w-full rounded-xl"
          style={{ height: dim.height }}
        />
      </div>

      <div className="mx-auto mt-7 max-w-[520px]">
        <Skeleton className="h-[50px] w-full rounded-xl" />
      </div>
    </div>
  );
}
