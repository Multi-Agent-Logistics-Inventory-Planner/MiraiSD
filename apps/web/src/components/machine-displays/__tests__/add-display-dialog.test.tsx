import { expect, it, vi } from "vitest";
import { act, render, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
const getProducts = vi.hoisted(() => vi.fn().mockResolvedValue([]));
vi.mock("@/lib/api/products", () => ({ getProducts }));
import { AddDisplayDialog } from "../add-display-dialog";

it("loads product options only while the add-display dialog is open", async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = (open: boolean) => <QueryClientProvider client={client}><AddDisplayDialog open={open} onOpenChange={vi.fn()} activeDisplays={[]} isSaving={false} onSubmit={vi.fn()} /></QueryClientProvider>;
  const { rerender } = render(view(false));
  expect(getProducts).not.toHaveBeenCalled();
  rerender(view(true));
  await waitFor(() => expect(getProducts).toHaveBeenCalledTimes(1));
  rerender(view(false));
  await act(async () => { await client.invalidateQueries(); });
  expect(getProducts).toHaveBeenCalledTimes(1);
});
