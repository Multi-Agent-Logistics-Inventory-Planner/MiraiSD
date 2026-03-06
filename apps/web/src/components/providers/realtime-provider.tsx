"use client";

import { useRealtimeBroadcast } from "@/hooks/realtime/use-realtime-broadcast";

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
  // Subscribe to broadcast channel for real-time updates from backend
  // This single subscription handles all entity types (inventory, products, shipments, etc.)
  useRealtimeBroadcast(enabled);

  return <>{children}</>;
}
