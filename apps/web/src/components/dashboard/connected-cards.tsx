"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { OrdersCard } from "./orders-card";
import { StockStatusCard } from "./stock-status-card";
import { HighestDemandCard } from "./highest-demand-card";
import { SupplyChainStatusCard } from "./supply-chain-status-card";
import { useProducts } from "@/hooks/queries/use-products";
import { useShipmentDisplayStatusCounts } from "@/hooks/queries/use-shipments";
import { useHighestDemandForecast } from "@/hooks/queries/use-forecasts";
import { getShipmentsPaged } from "@/lib/api/shipments";
import { type Shipment } from "@/types/api";

function getNextArrivingShipment(shipments: Shipment[]): Shipment | null {
  const withDates = shipments.filter(
    (s): s is Shipment & { expectedDeliveryDate: string } =>
      s.expectedDeliveryDate !== null && s.expectedDeliveryDate !== undefined
  );
  if (withDates.length === 0) return null;

  return withDates.sort(
    (a, b) =>
      new Date(a.expectedDeliveryDate).getTime() -
      new Date(b.expectedDeliveryDate).getTime()
  )[0];
}

/**
 * OrdersCard that fetches its own data for independent loading
 * Uses server-side counts instead of fetching all shipments for efficiency
 */
export function ConnectedOrdersCard() {
  const statusCountsQuery = useShipmentDisplayStatusCounts();

  return (
    <OrdersCard
      overdue={statusCountsQuery.data?.OVERDUE ?? 0}
      active={statusCountsQuery.data?.ACTIVE ?? 0}
      partial={statusCountsQuery.data?.PARTIAL ?? 0}
      completed={statusCountsQuery.data?.COMPLETED ?? 0}
      isLoading={statusCountsQuery.isLoading}
    />
  );
}

/**
 * StockStatusCard that fetches its own data for independent loading
 */
export function ConnectedStockStatusCard() {
  const productsQuery = useProducts();

  return (
    <StockStatusCard
      products={productsQuery.data ?? []}
      isLoading={productsQuery.isLoading}
    />
  );
}

/**
 * HighestDemandCard that fetches its own data for independent loading
 * Uses dedicated endpoint instead of fetching all forecasts for efficiency
 */
export function ConnectedHighestDemandCard() {
  const highestDemandQuery = useHighestDemandForecast();
  const productsQuery = useProducts();

  const highestDemandProduct = useMemo(() => {
    const forecast = highestDemandQuery.data;
    if (!forecast) return null;

    const product = productsQuery.data?.find((p) => p.id === forecast.itemId);

    return {
      ...forecast,
      imageUrl: product?.imageUrl ?? null,
    };
  }, [highestDemandQuery.data, productsQuery.data]);

  return (
    <HighestDemandCard
      product={highestDemandProduct}
      isLoading={highestDemandQuery.isLoading || productsQuery.isLoading}
    />
  );
}

/**
 * SupplyChainStatusCard that fetches its own data for independent loading
 * Fetches only active shipments (PENDING/IN_TRANSIT with no received items) to find next arriving
 */
export function ConnectedSupplyChainStatusCard() {
  const shipmentsQuery = useQuery({
    queryKey: ["shipments", "active-for-supply-chain"],
    queryFn: async () => {
      // Only fetch ACTIVE display status shipments (reduces payload significantly)
      const response = await getShipmentsPaged({ displayStatus: "ACTIVE" }, 0, 20);
      return response.content;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes - supply chain status doesn't need real-time updates
  });

  const supplyChainData = useMemo(() => {
    const shipments = shipmentsQuery.data ?? [];
    const nextShipment = getNextArrivingShipment(shipments);
    const additionalCount = shipments.length - (nextShipment ? 1 : 0);

    return {
      nextShipment,
      additionalCount,
    };
  }, [shipmentsQuery.data]);

  return (
    <SupplyChainStatusCard
      nextShipment={supplyChainData.nextShipment}
      additionalShipmentCount={supplyChainData.additionalCount}
      isLoading={shipmentsQuery.isLoading}
    />
  );
}
