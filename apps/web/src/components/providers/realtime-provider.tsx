"use client";

import { useRealtimeNotifications } from "@/hooks/realtime/use-realtime-notifications";
import { useRealtimeProducts } from "@/hooks/realtime/use-realtime-products";
import { useRealtimeInventory } from "@/hooks/realtime/use-realtime-inventory";
import { useRealtimeShipments } from "@/hooks/realtime/use-realtime-shipments";

interface RealtimeProviderProps {
  children: React.ReactNode;
  /** Enable/disable all realtime subscriptions */
  enabled?: boolean;
}

/**
 * Provider component that sets up global Supabase Realtime subscriptions.
 * Add this inside your QueryClientProvider to enable real-time updates across the app.
 *
 * @example
 * ```tsx
 * <QueryClientProvider client={queryClient}>
 *   <RealtimeProvider>
 *     <App />
 *   </RealtimeProvider>
 * </QueryClientProvider>
 * ```
 */
export function RealtimeProvider({ children, enabled = true }: RealtimeProviderProps) {
  // Subscribe to all real-time channels
  useRealtimeNotifications(enabled);
  useRealtimeProducts(enabled);
  useRealtimeInventory(enabled);
  useRealtimeShipments(enabled);

  return <>{children}</>;
}
