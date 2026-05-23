import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { NewTierSheet } from "../new-tier-sheet";
import type { LootboxTier } from "@/types/lootbox";

const mutateAsync = vi.fn();
const isPending = false;

vi.mock("@/hooks/mutations/use-lootbox-mutations", () => ({
  useCreateTierMutation: () => ({ mutateAsync, isPending }),
}));

vi.mock("@/hooks/use-toast", () => ({
  toast: vi.fn(),
}));

function tier(name: string, sortOrder = 0): LootboxTier {
  return {
    id: `tier-${name}`,
    name,
    probabilityPct: 25,
    displayColor: "#888888",
    sortOrder,
    active: true,
    prizes: [],
  };
}

describe("NewTierSheet", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue(undefined);
  });

  it("disables submit when name is empty", () => {
    render(
      <NewTierSheet
        lootboxId="crate-1"
        existingTiers={[]}
        onClose={() => {}}
      />
    );
    expect(screen.getByRole("button", { name: /add tier/i })).toBeDisabled();
  });

  it("flags duplicate names and blocks submit", () => {
    render(
      <NewTierSheet
        lootboxId="crate-1"
        existingTiers={[tier("RARE", 1)]}
        onClose={() => {}}
      />
    );
    fireEvent.change(screen.getByPlaceholderText(/e.g. MYTHIC/i), {
      target: { value: "rare" },
    });
    expect(
      screen.getByText(/a tier with this name already exists/i)
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /add tier/i })).toBeDisabled();
  });

  it("submits with probabilityPct=0, active=false, and next sortOrder", async () => {
    const onClose = vi.fn();
    render(
      <NewTierSheet
        lootboxId="crate-1"
        existingTiers={[tier("COMMON", 0), tier("RARE", 3)]}
        onClose={onClose}
      />
    );
    fireEvent.change(screen.getByPlaceholderText(/e.g. MYTHIC/i), {
      target: { value: "MYTHIC" },
    });

    fireEvent.click(screen.getByRole("button", { name: /add tier/i }));

    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledTimes(1);
    });
    expect(mutateAsync).toHaveBeenCalledWith({
      lootboxId: "crate-1",
      name: "MYTHIC",
      probabilityPct: 0,
      displayColor: "#8a8a93",
      sortOrder: 4,
      active: false,
    });
    expect(onClose).toHaveBeenCalled();
  });
});
