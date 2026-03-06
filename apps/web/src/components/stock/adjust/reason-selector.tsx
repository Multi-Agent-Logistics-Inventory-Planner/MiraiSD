"use client";

import { StockMovementReason } from "@/types/api";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { REASON_OPTIONS_BY_ACTION, type AdjustAction } from "./types";

interface ReasonSelectorProps {
  action: AdjustAction;
  value: StockMovementReason;
  onChange: (reason: StockMovementReason) => void;
  disabled: boolean;
}

export function ReasonSelector({
  action,
  value,
  onChange,
  disabled,
}: ReasonSelectorProps) {
  const options = REASON_OPTIONS_BY_ACTION[action];

  return (
    <div className="space-y-1.5">
      <Label className="text-sm text-muted-foreground">Reason</Label>
      <Select
        value={value}
        onValueChange={(v) => onChange(v as StockMovementReason)}
        disabled={disabled}
      >
        <SelectTrigger className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
