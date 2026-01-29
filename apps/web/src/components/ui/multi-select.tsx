"use client";

import * as React from "react";
import { ChevronDown, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";

export interface MultiSelectOption<T extends string> {
  value: T;
  label: string;
}

interface MultiSelectProps<T extends string> {
  options: MultiSelectOption<T>[];
  selected: T[];
  onChange: (selected: T[]) => void;
  placeholder?: string;
  label?: string;
  disabled?: boolean;
  className?: string;
}

export function MultiSelect<T extends string>({
  options,
  selected,
  onChange,
  placeholder = "Select...",
  label,
  disabled = false,
  className,
}: MultiSelectProps<T>) {
  const [open, setOpen] = React.useState(false);

  function handleToggle(value: T) {
    if (selected.includes(value)) {
      onChange(selected.filter((v) => v !== value));
    } else {
      onChange([...selected, value]);
    }
  }

  const displayText = React.useMemo(() => {
    if (selected.length === 0) {
      return placeholder;
    }
    if (selected.length === 1) {
      const option = options.find((o) => o.value === selected[0]);
      return option?.label ?? placeholder;
    }
    return `${selected.length} selected`;
  }, [selected, options, placeholder]);

  const hasSelection = selected.length > 0;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className={cn(
            "h-9 justify-between gap-1 font-normal",
            !hasSelection && "text-muted-foreground",
            className
          )}
        >
          <span className="truncate">{displayText}</span>
          <ChevronDown className="h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-48 p-1" align="start">
        {label && (
          <div className="px-2 py-1.5 text-xs text-muted-foreground font-medium">
            {label}
          </div>
        )}
        <div className="max-h-60 overflow-y-auto">
          {options.length === 0 ? (
            <div className="py-2 px-3 text-sm text-muted-foreground">
              No options available
            </div>
          ) : (
            options.map((option) => {
              const isSelected = selected.includes(option.value);
              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => handleToggle(option.value)}
                  className={cn(
                    "flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none cursor-pointer",
                    "hover:bg-accent hover:text-accent-foreground",
                    "focus:bg-accent focus:text-accent-foreground"
                  )}
                >
                  <div
                    className={cn(
                      "flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border",
                      isSelected
                        ? "border-primary bg-primary text-primary-foreground"
                        : "border-input"
                    )}
                  >
                    {isSelected && <Check className="h-3 w-3" />}
                  </div>
                  <span className="truncate">{option.label}</span>
                </button>
              );
            })
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
