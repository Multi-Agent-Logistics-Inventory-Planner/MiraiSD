import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RecentActivitySection } from "../recent-activity-section";
import type { AdminCoinActivity } from "@/types/lootbox";

vi.mock("@/hooks/queries/use-lootbox", () => ({
  useAdminCoinActivity: () => ({ data: mockActivity, isLoading: false }),
}));

const mockActivity: AdminCoinActivity[] = [
  {
    id: "a-1",
    userId: "u-1",
    userName: "Sarah Chen",
    delta: 15,
    reason: "May review bonus",
    occurredAt: new Date().toISOString(),
    kind: "ADJUSTMENT",
  },
  {
    id: "a-2",
    userId: "u-2",
    userName: "Marcus Webb",
    delta: -3,
    reason: "Refund — wrong crate",
    occurredAt: new Date().toISOString(),
    kind: "ADJUSTMENT",
  },
];

describe("RecentActivitySection", () => {
  it("renders positive deltas in green and negative in red", () => {
    render(<RecentActivitySection onViewAll={() => {}} />);
    const pos = screen.getByText("+15");
    const neg = screen.getByText("−3");
    expect(pos).toHaveStyle({ color: "rgb(127, 217, 154)" });
    expect(neg).toHaveStyle({ color: "rgb(239, 138, 157)" });
  });

  it("fires onViewAll when the View all button is clicked", () => {
    const onViewAll = vi.fn();
    render(<RecentActivitySection onViewAll={onViewAll} />);
    fireEvent.click(screen.getByRole("button", { name: /view all/i }));
    expect(onViewAll).toHaveBeenCalledTimes(1);
  });
});
