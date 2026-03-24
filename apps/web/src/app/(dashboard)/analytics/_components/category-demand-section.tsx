"use client";

import { useState } from "react";
import { useDemandLeaders } from "@/hooks/queries/use-demand-leaders";
import type { DemandLeadersPeriod } from "@/types/analytics";
import { CategoryDemandDonut } from "./category-demand-donut";
import { CategoryRankingList } from "./category-ranking-list";

export function CategoryDemandSection() {
  const [period, setPeriod] = useState<DemandLeadersPeriod>("30d");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const { data, isLoading } = useDemandLeaders(period);

  return (
    <div className="grid lg:grid-cols-5 border dark:border-none bg-card/95 dark:bg-[#2b2b29] rounded-2xl">
      <div className="lg:col-span-2">
        <CategoryDemandDonut
          rankings={data?.categoryRankings}
          isLoading={isLoading}
          period={period}
          onPeriodChange={setPeriod}
          selectedCategory={selectedCategory}
          onCategoryClick={setSelectedCategory}
        />
      </div>
      <div className="lg:col-span-3">
        <CategoryRankingList
          rankings={data?.categoryRankings}
          isLoading={isLoading}
          selectedCategory={selectedCategory}
        />
      </div>
    </div>
  );
}
