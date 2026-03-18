"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { OrdersCard } from "./orders-card";
import { StockStatusCard } from "./stock-status-card";
import { HighestDemandCard } from "./highest-demand-card";
import { SupplyChainStatusCard } from "./supply-chain-status-card";
import { useProducts } from "@/hooks/queries/use-products";
import { useShipmentDisplayStatusCounts } from "@/hooks/queries/use-shipments";
import { useAllForecasts } from "@/hooks/queries/use-forecasts";
import { getShipmentsPaged } from "@/lib/api/shipments";
import { ShipmentStatus, type Shipment } from "@/types/api";

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
 */
export function ConnectedOrdersCard() {
  const statusCountsQuery = useShipmentDisplayStatusCounts();
  const shipmentsQuery = useQuery({
    queryKey: ["shipments", "all-recent"],
    queryFn: async () => {
      const response = await getShipmentsPaged({}, 0, 200);
      return response.content;
    },
    staleTime: 60 * 1000,
  });

  const overdueCount = useMemo(() => {
    const shipments = shipmentsQuery.data ?? [];
    const now = new Date();
    return shipments.filter((s) => {
      if (!s.expectedDeliveryDate) return false;
      return (
        new Date(s.expectedDeliveryDate) < now &&
        s.status !== ShipmentStatus.DELIVERED &&
        s.status !== ShipmentStatus.CANCELLED
      );
    }).length;
  }, [shipmentsQuery.data]);

  return (
    <OrdersCard
      overdue={overdueCount}
      active={statusCountsQuery.data?.ACTIVE ?? 0}
      partial={statusCountsQuery.data?.PARTIAL ?? 0}
      completed={statusCountsQuery.data?.COMPLETED ?? 0}
      isLoading={shipmentsQuery.isLoading || statusCountsQuery.isLoading}
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
 */
export function ConnectedHighestDemandCard() {
  const allForecastsQuery = useAllForecasts();
  const productsQuery = useProducts();

  const highestDemandProduct = useMemo(() => {
    const forecasts = allForecastsQuery.data ?? [];
    const consuming = forecasts.filter((f) => f.avgDailyDelta < 0);
    if (!consuming.length) return null;

    const sorted = [...consuming].sort((a, b) => a.avgDailyDelta - b.avgDailyDelta);
    const top = sorted[0];
    const product = productsQuery.data?.find((p) => p.id === top.itemId);

    return {
      ...top,
      imageUrl: product?.imageUrl ?? null,
    };
  }, [allForecastsQuery.data, productsQuery.data]);

  return (
    <HighestDemandCard
      product={highestDemandProduct}
      isLoading={allForecastsQuery.isLoading || productsQuery.isLoading}
    />
  );
}

/**
 * SupplyChainStatusCard that fetches its own data for independent loading
 */
export function ConnectedSupplyChainStatusCard() {
  const shipmentsQuery = useQuery({
    queryKey: ["shipments", "all-recent"],
    queryFn: async () => {
      const response = await getShipmentsPaged({}, 0, 200);
      return response.content;
    },
    staleTime: 60 * 1000,
  });

  const supplyChainData = useMemo(() => {
    const shipments = shipmentsQuery.data ?? [];
    const activeShipments = shipments.filter(
      (s) => s.status === ShipmentStatus.PENDING || s.status === ShipmentStatus.IN_TRANSIT
    );
    const nextShipment = getNextArrivingShipment(activeShipments);
    const additionalCount = activeShipments.length - (nextShipment ? 1 : 0);

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
