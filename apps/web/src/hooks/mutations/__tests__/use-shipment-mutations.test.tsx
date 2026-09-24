import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const site = vi.hoisted(() => ({ siteId: "main" as string | undefined }));
const api = vi.hoisted(() => ({
  createSiteShipment: vi.fn(),
  updateSiteShipment: vi.fn(),
  deleteSiteShipment: vi.fn(),
  receiveSiteShipment: vi.fn(),
  undoReceiveSiteShipmentItem: vi.fn(),
  undoReceiveSiteShipmentItems: vi.fn(),
  overrideSiteShipmentStatus: vi.fn(),
}));
vi.mock("@/hooks/queries/use-current-site", () => ({ useCurrentSite: () => site }));
vi.mock("@/lib/api/site-shipments", () => api);

import {
  useCreateShipmentMutation,
  useUpdateShipmentMutation,
  useDeleteShipmentMutation,
  useReceiveShipmentMutation,
  useUndoReceiveShipmentItemMutation,
  useUndoReceiveShipmentItemsMutation,
  useOverrideShipmentStatusMutation,
} from "../use-shipment-mutations";

function setup() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const hook = renderHook(
    () => ({
      create: useCreateShipmentMutation(),
      update: useUpdateShipmentMutation(),
      remove: useDeleteShipmentMutation(),
      receive: useReceiveShipmentMutation(),
      undoItem: useUndoReceiveShipmentItemMutation(),
      undoItems: useUndoReceiveShipmentItemsMutation(),
      override: useOverrideShipmentStatusMutation(),
    }),
    {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    }
  );
  return hook;
}

/**
 * .specs/phase-7-shipments T-5/T-6, external review finding: every wrapper here must forward
 * `mutate`/`mutateAsync`'s second `options` argument to the underlying `useMutation` call, or a
 * caller's `onSuccess`/`onError`/`onSettled` callback silently never runs. An earlier pass fixed
 * only create/update/override and missed delete/receive/undoItem/undoItems; this covers all
 * seven so that regression can't recur unnoticed.
 */
describe("use-shipment-mutations option forwarding", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    site.siteId = "main";
    api.createSiteShipment.mockResolvedValue({});
    api.updateSiteShipment.mockResolvedValue({});
    api.deleteSiteShipment.mockResolvedValue(undefined);
    api.receiveSiteShipment.mockResolvedValue({});
    api.undoReceiveSiteShipmentItem.mockResolvedValue({});
    api.undoReceiveSiteShipmentItems.mockResolvedValue({});
    api.overrideSiteShipmentStatus.mockResolvedValue({});
  });

  it("runs a per-call onSuccess for every mutation hook", async () => {
    const { result } = setup();
    const onSuccess = {
      create: vi.fn(),
      update: vi.fn(),
      remove: vi.fn(),
      receive: vi.fn(),
      undoItem: vi.fn(),
      undoItems: vi.fn(),
      override: vi.fn(),
    };

    await act(async () => {
      await result.current.create.mutateAsync(
        { shipmentNumber: "S1", orderDate: "2026-01-01", items: [] },
        { onSuccess: onSuccess.create }
      );
      await result.current.update.mutateAsync(
        { id: "s1", payload: { shipmentNumber: "S1", orderDate: "2026-01-01", items: [] } },
        { onSuccess: onSuccess.update }
      );
      await result.current.remove.mutateAsync({ id: "s1" }, { onSuccess: onSuccess.remove });
      await result.current.receive.mutateAsync(
        { id: "s1", payload: { actualDeliveryDate: "2026-01-01", receivedBy: "u1", itemReceipts: [] } },
        { onSuccess: onSuccess.receive }
      );
      await result.current.undoItem.mutateAsync(
        { shipmentId: "s1", itemId: "i1" },
        { onSuccess: onSuccess.undoItem }
      );
      await result.current.undoItems.mutateAsync(
        { shipmentId: "s1", itemIds: ["i1"] },
        { onSuccess: onSuccess.undoItems }
      );
      await result.current.override.mutateAsync(
        { id: "s1", payload: { status: "PENDING" as never, reason: "test" } },
        { onSuccess: onSuccess.override }
      );
    });

    for (const mock of Object.values(onSuccess)) {
      expect(mock).toHaveBeenCalledTimes(1);
    }
  });
});
