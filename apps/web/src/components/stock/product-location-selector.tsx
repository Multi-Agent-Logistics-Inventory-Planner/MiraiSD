"use client";

import { useMemo } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ProductInventoryEntry, LocationType } from "@/types/api";
import { LOCATION_TYPE_LABELS } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

interface ProductLocationSelectorProps {
  /** The inventory entries for the product (locations where it exists) */
  inventoryEntries: ProductInventoryEntry[];
  /** Current selected location */
  value: LocationSelection;
  /** Called when location selection changes */
  onChange: (value: LocationSelection) => void;
  /** Whether the selector is disabled */
  disabled?: boolean;
}

/**
 * A simplified location selector that only shows locations where a specific product exists.
 * Used in product-filtered mode of AdjustStockDialog.
 */
export function ProductLocationSelector({
  inventoryEntries,
  value,
  onChange,
  disabled = false,
}: ProductLocationSelectorProps) {
  // Sort entries by location code for consistent ordering
  const sortedEntries = useMemo(() => {
    return [...inventoryEntries].sort((a, b) => {
      // Sort by location type first, then by code
      if (a.locationType !== b.locationType) {
        return a.locationType.localeCompare(b.locationType);
      }
      return a.locationCode.localeCompare(b.locationCode, undefined, {
        numeric: true,
      });
    });
  }, [inventoryEntries]);

  // Build the selected value string for the Select component
  const selectedValue = value.locationId ?? "";

  function handleValueChange(selectedValue: string) {
    // Find entry by locationId or inventoryId (for NOT_ASSIGNED which has no locationId)
    const entry = inventoryEntries.find(
      (e) => e.locationId === selectedValue || e.inventoryId === selectedValue
    );
    if (!entry) return;

    onChange({
      locationType: entry.locationType as LocationType,
      // For NOT_ASSIGNED, use inventoryId as locationId since there's no actual location
      locationId: entry.locationId || entry.inventoryId,
      locationCode: entry.locationCode,
    });
  }

  // Helper to format display label (e.g., "B15 - Box Bin (30)")
  function formatEntryLabel(entry: ProductInventoryEntry): string {
    const typeLabel = LOCATION_TYPE_LABELS[entry.locationType as LocationType] ?? entry.locationType;
    // locationCode already includes the prefix (e.g., "B1" for Box Bin 1)
    return `${entry.locationCode} - ${typeLabel} (${entry.quantity})`;
  }

  // Format the trigger display text
  function formatTriggerLabel(): string {
    if (!value.locationId) return "";
    // Find entry by locationId or inventoryId (for NOT_ASSIGNED)
    const entry = inventoryEntries.find(
      (e) => e.locationId === value.locationId || e.inventoryId === value.locationId
    );
    if (!entry) return "";
    // locationCode already includes the prefix (e.g., "B1" for Box Bin 1)
    return entry.locationCode;
  }

  if (inventoryEntries.length === 0) {
    return (
      <div className="text-sm text-muted-foreground">
        No locations available
      </div>
    );
  }

  return (
    <Select
      value={selectedValue}
      onValueChange={handleValueChange}
      disabled={disabled}
    >
      <SelectTrigger className="w-full">
        <SelectValue placeholder="Select location">
          {selectedValue ? formatTriggerLabel() : "Select location"}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        {sortedEntries.map((entry) => (
          <SelectItem
            key={entry.inventoryId}
            value={entry.locationId ?? entry.inventoryId}
          >
            {formatEntryLabel(entry)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
