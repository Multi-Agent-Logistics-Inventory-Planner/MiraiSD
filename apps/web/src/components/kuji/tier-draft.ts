import type { LocationSelection } from "@/types/transfer";

export type TierMode = "existing" | "create";

export interface DraftTier {
  tempId: string;
  label: string;
  letter: string;
  mode: TierMode;
  /** Used when mode === "existing". */
  linkedProductId: string;
  /** Used when mode === "existing". Cached for display. */
  linkedProductDisplayName: string;
  /** Used when mode === "existing". Cached for display. */
  linkedProductDisplaySku: string | null;
  /** Used when mode === "existing". */
  source: LocationSelection;
  /** Used when mode === "create". */
  productName: string;
  /** Used when mode === "create". Pending File before upload. */
  productImageFile: File | null;
  /** Local object URL for preview before upload. */
  productImagePreviewUrl: string | null;
  /** Already-uploaded URL (e.g. cloned from a previous box). */
  productImageUrl: string;
  count: number | "";
  heldBack: number | "";
  price: string;
}

export const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

export function blankTier(): DraftTier {
  return {
    tempId: crypto.randomUUID(),
    label: "",
    letter: "",
    mode: "create",
    linkedProductId: "",
    linkedProductDisplayName: "",
    linkedProductDisplaySku: null,
    source: EMPTY_LOCATION,
    productName: "",
    productImageFile: null,
    productImagePreviewUrl: null,
    productImageUrl: "",
    count: "",
    heldBack: "",
    price: "",
  };
}

export interface TierValidationErrors {
  [key: string]: string;
}

export function validateTier(
  tier: DraftTier,
  errors: TierValidationErrors,
): void {
  if (!tier.label.trim()) {
    errors[`tier:${tier.tempId}:label`] = "Label is required";
  }
  if (
    tier.count === "" ||
    (typeof tier.count === "number" && tier.count < 0)
  ) {
    errors[`tier:${tier.tempId}:count`] = "Slips must be ≥ 0";
  }
  if (typeof tier.heldBack === "number" && tier.heldBack < 0) {
    errors[`tier:${tier.tempId}:heldBack`] = "Held back must be ≥ 0";
  }
  if (tier.mode === "existing") {
    if (!tier.linkedProductId) {
      errors[`tier:${tier.tempId}:linkedProduct`] = "Pick a product";
    } else if (!tier.source.locationType || !tier.source.locationId) {
      errors[`tier:${tier.tempId}:source`] =
        "Source location is required when linking a product";
    }
  } else if (!tier.productName.trim()) {
    errors[`tier:${tier.tempId}:productName`] = "Prize name is required";
  }
  if (tier.price && Number.isNaN(parseFloat(tier.price))) {
    errors[`tier:${tier.tempId}:price`] = "Price must be a number";
  }
}
