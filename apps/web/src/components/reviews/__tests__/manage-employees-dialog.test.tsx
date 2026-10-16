import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";

const mockUseCurrentSite = vi.fn();
const mockGetUsers = vi.fn();

vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

vi.mock("@/lib/api/site-reviews", () => ({
  getSiteAllUsersForReviewManagement: (...args: unknown[]) => mockGetUsers(...args),
  updateSiteUserReviewTracking: vi.fn(),
}));

import { ManageEmployeesDialog } from "../manage-employees-dialog";

function renderOpen() {
  return render(<ManageEmployeesDialog open onOpenChange={vi.fn()} onSuccess={vi.fn()} />);
}

describe("ManageEmployeesDialog site handling", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads the resolved site's users", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetUsers.mockResolvedValue([]);

    renderOpen();

    await waitFor(() => expect(mockGetUsers).toHaveBeenCalledWith("site-1"));
  });

  it("stops showing the loading spinner when the site settles without a siteId", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: false, error: null });

    const { baseElement } = renderOpen();

    expect(mockGetUsers).not.toHaveBeenCalled();
    await waitFor(() => expect(baseElement.querySelector(".animate-spin")).toBeNull());
    expect(screen.queryByText(/no site/i)).not.toBeNull();
  });

  it("keeps the spinner while the site is still resolving", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });

    const { baseElement } = renderOpen();

    expect(baseElement.querySelector(".animate-spin")).not.toBeNull();
  });
});
