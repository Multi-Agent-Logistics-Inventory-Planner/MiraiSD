import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PlayerBalancesSection } from "../player-balances-section";
import type { PlayerCoinRow } from "@/types/lootbox";

vi.mock("@/hooks/queries/use-lootbox", () => ({
  usePlayerCoinRows: () => ({ data: mockRows, isLoading: false }),
}));

vi.mock("@/hooks/mutations/use-lootbox-mutations", () => ({
  useAdjustCoinsMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/use-toast", () => ({
  toast: vi.fn(),
}));

const mockRows: PlayerCoinRow[] = [
  {
    userId: "u-alice",
    fullName: "Alice Anderson",
    email: "alice@example.com",
    balance: 100,
    lastChangeDelta: 5,
    lastChangeAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
  },
  {
    userId: "u-bob",
    fullName: "Bob Brown",
    email: "bob@example.com",
    balance: 47,
    lastChangeDelta: -3,
    lastChangeAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
  },
];

describe("PlayerBalancesSection", () => {
  it("renders all rows with player names and balances", () => {
    render(<PlayerBalancesSection grantTargetUsers={[]} />);
    expect(screen.getByText("Alice Anderson")).toBeInTheDocument();
    expect(screen.getByText("Bob Brown")).toBeInTheDocument();
    expect(screen.getByText("100")).toBeInTheDocument();
    expect(screen.getByText("47")).toBeInTheDocument();
  });

  it("paints a negative last-change with the red color", () => {
    render(<PlayerBalancesSection grantTargetUsers={[]} />);
    // U+2212 minus + abs
    const cell = screen.getByText((c) => c.startsWith("−3"));
    expect(cell).toHaveClass("text-[#ef8a9d]");
  });

  it("filters players client-side by case-insensitive substring on name or email", () => {
    render(<PlayerBalancesSection grantTargetUsers={[]} />);
    const search = screen.getByPlaceholderText(/search players/i);
    fireEvent.change(search, { target: { value: "BOB" } });
    expect(screen.queryByText("Alice Anderson")).not.toBeInTheDocument();
    expect(screen.getByText("Bob Brown")).toBeInTheDocument();
  });

  it("shows empty-state message when no players match the search", () => {
    render(<PlayerBalancesSection grantTargetUsers={[]} />);
    fireEvent.change(screen.getByPlaceholderText(/search players/i), {
      target: { value: "zzzzz" },
    });
    expect(screen.getByText(/no players match your search/i)).toBeInTheDocument();
  });
});
