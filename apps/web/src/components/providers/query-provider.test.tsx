import { render, screen, act } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn().mockResolvedValue({ data: [] }) }));
vi.mock("@/lib/api/generated-client", () => ({
  webApiClient: { GET: get },
  unwrapGeneratedResponse: (result: { data: unknown }) => result.data,
}));
vi.mock("@/lib/supabase", () => ({ getSupabaseClient: () => null }));
vi.mock("@tanstack/react-query-devtools", () => ({ ReactQueryDevtools: () => null }));

import { QueryProvider } from "./query-provider";

describe("public query context", () => {
  it("renders the login page without requesting authenticated membership data", async () => {
    await act(async () => {
      render(<QueryProvider><div>Sign in</div></QueryProvider>);
    });
    expect(screen.getByText("Sign in")).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });
});
