"use client";

import { useRealtimeBroadcast } from "@/hooks/realtime/use-realtime-broadcast";
import { useAuthContext } from "./auth-provider";

interface RealtimeProviderProps {
  children: React.ReactNode;
  /** Enable/disable all realtime subscriptions */
  enabled?: boolean;
}

/**
 * Dashboard realtime lives inside both QueryProvider and AuthProvider. Mount the
 * subscriber only after authentication: the hook also fetches protected site
 * membership data, even when its subscription's enabled flag is false.
 */
export function RealtimeProvider({ children, enabled = true }: RealtimeProviderProps) {
  const { user, isLoading } = useAuthContext();

  return (
    <>
      {enabled && !isLoading && user ? <RealtimeSubscriber /> : null}
      {children}
    </>
  );
}

function RealtimeSubscriber() {
  useRealtimeBroadcast();
  return null;
}
