import { expect, it, vi } from "vitest";
import { render } from "@testing-library/react";
import { LocationType } from "@/types/api";
const useAuth = vi.hoisted(() => vi.fn());
vi.mock("@/hooks/use-auth", () => ({ useAuth }));
import { LocationDetailSheet } from "../location-detail-sheet";

it("does not mount query-owning contents for a closed sheet with a retained location", () => {
  const { container } = render(<LocationDetailSheet open={false} onOpenChange={vi.fn()} locationType={LocationType.GACHAPON} location={{ id: "machine-1", locationCode: "G1", storageLocationId: "category-1", storageLocationType: "GACHAPON", createdAt: "", updatedAt: "" }} onEdit={vi.fn()} />);
  expect(container).toBeEmptyDOMElement();
  expect(useAuth).not.toHaveBeenCalled();
});
