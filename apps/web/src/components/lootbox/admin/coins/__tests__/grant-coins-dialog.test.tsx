import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { GrantCoinsDialog } from "../grant-coins-dialog";

const mutateAsync = vi.fn();

vi.mock("@/hooks/mutations/use-lootbox-mutations", () => ({
  useAdjustCoinsMutation: () => ({ mutateAsync, isPending: false }),
}));

vi.mock("@/hooks/use-toast", () => ({
  toast: vi.fn(),
}));

describe("GrantCoinsDialog", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({});
  });

  it("submits the signed amount and reason after picking a user", async () => {
    const onOpenChange = vi.fn();
    render(
      <GrantCoinsDialog
        open
        onOpenChange={onOpenChange}
        users={[{ id: "u-1", fullName: "Alice", email: "a@x.com" }]}
        initialUserId="u-1"
      />
    );

    fireEvent.change(screen.getByLabelText(/reason/i), {
      target: { value: "Q3 launch" },
    });
    fireEvent.change(screen.getByLabelText(/amount/i), {
      target: { value: "5" },
    });
    fireEvent.click(screen.getByRole("button", { name: /apply \+5 coins/i }));

    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith({
        userId: "u-1",
        delta: 5,
        reason: "Q3 launch",
      });
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("disables submit when amount is zero or reason is empty", () => {
    render(
      <GrantCoinsDialog
        open
        onOpenChange={() => {}}
        users={[{ id: "u-1", fullName: "Alice", email: "a@x.com" }]}
        initialUserId="u-1"
      />
    );
    // empty reason + empty amount → disabled
    expect(screen.getByRole("button", { name: /^apply$/i })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/amount/i), {
      target: { value: "0" },
    });
    expect(screen.getByRole("button", { name: /^apply$/i })).toBeDisabled();
  });
});
