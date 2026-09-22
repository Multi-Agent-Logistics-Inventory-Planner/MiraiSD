import { useEffect } from "react";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { auth, subscribe, unsubscribe } = vi.hoisted(() => ({
  auth: { user: null as { id: string } | null, isLoading: true },
  subscribe: vi.fn(),
  unsubscribe: vi.fn(),
}));
vi.mock("./auth-provider", () => ({ useAuthContext: () => auth }));
vi.mock("@/hooks/realtime/use-realtime-broadcast", () => ({
  useRealtimeBroadcast: function useMockRealtime() {
    useEffect(() => {
      subscribe();
      return unsubscribe;
    }, []);
  },
}));

import { RealtimeProvider } from "./realtime-provider";

describe("dashboard realtime authentication lifecycle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auth.user = null;
    auth.isLoading = true;
  });

  it("waits for validation, starts after sign-in, and cleans up on sign-out", () => {
    const view = () => <RealtimeProvider><div>Dashboard</div></RealtimeProvider>;
    const { rerender } = render(view());
    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(subscribe).not.toHaveBeenCalled();

    auth.user = { id: "user-1" };
    rerender(view());
    expect(subscribe).not.toHaveBeenCalled();

    auth.isLoading = false;
    rerender(view());
    expect(subscribe).toHaveBeenCalledTimes(1);

    auth.user = null;
    rerender(view());
    expect(unsubscribe).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Dashboard")).toBeInTheDocument();
  });

  it("does not mount the site-fetching hook when explicitly disabled", () => {
    auth.user = { id: "user-1" };
    auth.isLoading = false;
    render(<RealtimeProvider enabled={false}><div>Dashboard</div></RealtimeProvider>);
    expect(subscribe).not.toHaveBeenCalled();
  });
});
