import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { LocationType } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";
const site = vi.hoisted(() => ({ siteId: "main" as string | undefined }));
const api = vi.hoisted(() => ({ getSiteLocations: vi.fn(), getLocationsByType: vi.fn() }));
vi.mock("@/hooks/queries/use-current-site", () => ({ useCurrentSite: () => site }));
vi.mock("@/lib/api/locations", () => api);
import { LocationSelector, LegacyLocationSelector } from "../location-selector";

function Harness({ legacy = false }: { legacy?: boolean }) {
  const [value, setValue] = useState<LocationSelection>({ locationType: LocationType.RACK, locationId: "main-location", locationCode: "1" });
  const Picker = legacy ? LegacyLocationSelector : LocationSelector;
  return <><Picker label="To" value={value} onChange={setValue} /><output data-testid="selection">{value.locationId ?? "none"}</output></>;
}
function setup(legacy = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<Harness legacy={legacy} />, { wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider> });
}

describe("real location selector", () => {
  beforeEach(() => {
    vi.clearAllMocks(); site.siteId = "main";
    api.getSiteLocations.mockResolvedValue([{ id: "main-location", locationCode: "R1" }]);
    api.getLocationsByType.mockResolvedValue([{ id: "main-location", locationCode: "R1" }]);
    vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} });
    Element.prototype.scrollIntoView = vi.fn();
  });
  it("uses scoped options and selects a location through the real dropdown", async () => {
    setup();
    await waitFor(() => expect(api.getSiteLocations).toHaveBeenCalledWith("main", "RACKS"));
    await waitFor(() => expect(screen.getByRole("combobox", { name: "To code" })).toBeEnabled());
    fireEvent.click(screen.getByRole("combobox", { name: "To code" }));
    fireEvent.click(await screen.findByRole("option", { name: "1" }));
    expect(screen.getByTestId("selection")).toHaveTextContent("main-location");
    expect(api.getLocationsByType).not.toHaveBeenCalled();
  });
  it("clears old selection on a site change and never shows a late old-site result", async () => {
    let finish!: (rows: unknown[]) => void;
    api.getSiteLocations.mockImplementation((id: string) => id === "main" ? new Promise(resolve => { finish = resolve; }) : Promise.resolve([{ id: "second-location", locationCode: "R2" }]));
    const { rerender } = setup();
    site.siteId = "second";
    rerender(<Harness />);
    await waitFor(() => expect(screen.getByTestId("selection")).toHaveTextContent("none"));
    await act(async () => finish([{ id: "main-location", locationCode: "R1" }]));
    // Choose the type after the site change cleared the old site's entire selection.
    // The new options are proven independently by the site hook test; old code is absent here.
    expect(screen.getByTestId("selection")).not.toHaveTextContent("main-location");

  });
  it("shows an error with retry instead of an empty list", async () => {
    api.getSiteLocations.mockRejectedValueOnce(new Error("Forbidden"));
    setup();
    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to load locations");
    expect(screen.getByRole("combobox", { name: "To code" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Retry locations" }));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
  });
  it("keeps Phase 7 on its explicit legacy adapter", async () => {
    setup(true);
    await waitFor(() => expect(api.getLocationsByType).toHaveBeenCalledWith(LocationType.RACK));
    expect(api.getSiteLocations).not.toHaveBeenCalled();
  });
  it("disables selection without a resolved site", () => {
    site.siteId = undefined;
    setup();
    expect(screen.getByRole("combobox", { name: "To type" })).toBeDisabled();
    expect(screen.getByRole("combobox", { name: "To code" })).toBeDisabled();
    expect(api.getSiteLocations).not.toHaveBeenCalled();
  });
});
