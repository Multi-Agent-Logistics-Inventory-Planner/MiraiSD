"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { StockMovementReason } from "@/types/api";

export type MovementReasonFilter = StockMovementReason | "all";

export interface MovementFiltersState {
  productId: string;
  reason: MovementReasonFilter;
  actorId: string;
  locationId: string;
  fromDate: string;
  toDate: string;
}

export const DEFAULT_MOVEMENT_FILTERS: MovementFiltersState = {
  productId: "",
  reason: "all",
  actorId: "",
  locationId: "",
  fromDate: "",
  toDate: "",
};

export interface MovementFilterProduct {
  id: string;
  sku: string;
  name: string;
}

interface MovementFiltersProps {
  state: MovementFiltersState;
  onChange: (next: MovementFiltersState) => void;
  products?: MovementFilterProduct[];
}

export function MovementFilters({
  state,
  onChange,
  products,
}: MovementFiltersProps) {
  const hasActiveFilters =
    state.reason !== "all" ||
    Boolean(state.actorId) ||
    Boolean(state.locationId) ||
    Boolean(state.fromDate) ||
    Boolean(state.toDate);

  const productOptions = products ?? [];

  const updateField = <K extends keyof MovementFiltersState>(
    key: K,
    value: MovementFiltersState[K]
  ) => {
    onChange({ ...state, [key]: value });
  };

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {productOptions.length > 0 ? (
        <div className="grid gap-2">
          <Label>Product</Label>
          <Select
            value={state.productId || "__all__"}
            onValueChange={(v) =>
              updateField("productId", v === "__all__" ? "" : v)
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="All products" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">All products</SelectItem>
              {productOptions.map((product) => (
                <SelectItem key={product.id} value={product.id}>
                  {product.sku} - {product.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      ) : null}

      <div className="grid gap-2">
        <Label>Reason</Label>
        <Select
          value={state.reason}
          onValueChange={(v) => updateField("reason", v as MovementReasonFilter)}
        >
          <SelectTrigger>
            <SelectValue placeholder="All reasons" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All reasons</SelectItem>
            {Object.values(StockMovementReason).map((reason) => (
              <SelectItem key={reason} value={reason}>
                {reason}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="grid gap-2">
        <Label>Actor ID</Label>
        <Input
          placeholder="Filter by actor"
          value={state.actorId}
          onChange={(e) => updateField("actorId", e.target.value)}
        />
      </div>

      <div className="grid gap-2">
        <Label>Location ID</Label>
        <Input
          placeholder="From or to location"
          value={state.locationId}
          onChange={(e) => updateField("locationId", e.target.value)}
        />
      </div>

      <div className="grid gap-2">
        <Label>From</Label>
        <Input
          type="date"
          value={state.fromDate}
          onChange={(e) => updateField("fromDate", e.target.value)}
        />
      </div>

      <div className="grid gap-2">
        <Label>To</Label>
        <Input
          type="date"
          value={state.toDate}
          onChange={(e) => updateField("toDate", e.target.value)}
        />
      </div>

      <div className="flex items-end">
        <Button
          type="button"
          variant="outline"
          className="w-full"
          disabled={!hasActiveFilters}
          onClick={() => onChange({ ...DEFAULT_MOVEMENT_FILTERS, productId: state.productId })}
        >
          Clear filters
        </Button>
      </div>
    </div>
  );
}
