"use client";

import { useForecastAccuracy } from "@/hooks/queries/use-forecast-accuracy";
import {
  AccuracyHeader,
  AccuracySkeleton,
  ByCategoryTable,
  DefinitionStrip,
  HealthBanner,
  KpiRow,
} from "@/components/analytics/accuracy";

export function TabAccuracy() {
  const { data, isLoading, isError } = useForecastAccuracy();

  if (isLoading) {
    return (
      <div className="space-y-4">
        <AccuracyHeader />
        <AccuracySkeleton />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="space-y-4">
        <AccuracyHeader />
        <p className="text-sm text-muted-foreground">
          Could not load forecast accuracy right now.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <AccuracyHeader />
      <HealthBanner headline={data.headline} comparison={data.comparison} />
      <KpiRow headline={data.headline} comparison={data.comparison} />
      <DefinitionStrip />
      <ByCategoryTable rows={data.byCategory} />
    </div>
  );
}
