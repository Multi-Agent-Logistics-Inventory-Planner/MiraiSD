"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { Check, ChevronsUpDown, Plus } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { useSupplierSearch } from "@/hooks/queries/use-suppliers";
import { useCreateSupplierMutation } from "@/hooks/mutations/use-supplier-mutations";
import type { Supplier } from "@/types/api";

interface SupplierAutocompleteProps {
  value?: string | null;
  displayValue?: string | null;
  onChange: (supplierId: string | null, displayName: string | null) => void;
  placeholder?: string;
  disabled?: boolean;
}

export function SupplierAutocomplete({
  value,
  displayValue,
  onChange,
  placeholder = "Select supplier...",
  disabled = false,
}: SupplierAutocompleteProps) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const debounceRef = useRef<NodeJS.Timeout>(undefined);

  const [debouncedSearch, setDebouncedSearch] = useState("");

  // Debounce search input
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [search]);

  const { data: suppliers = [], isLoading } = useSupplierSearch(debouncedSearch);
  const createMutation = useCreateSupplierMutation();

  const handleSelect = useCallback(
    (supplier: Supplier) => {
      onChange(supplier.id, supplier.displayName);
      setOpen(false);
      setSearch("");
    },
    [onChange]
  );

  const handleCreateNew = useCallback(async () => {
    if (!search.trim()) return;
    try {
      const newSupplier = await createMutation.mutateAsync({
        displayName: search.trim(),
      });
      onChange(newSupplier.id, newSupplier.displayName);
      setOpen(false);
      setSearch("");
    } catch {
      // Toast will be shown by mutation error handler
    }
  }, [search, createMutation, onChange]);

  const handleClear = useCallback(() => {
    onChange(null, null);
  }, [onChange]);

  const showCreateOption =
    search.trim() &&
    !isLoading &&
    !suppliers.some(
      (s) => s.displayName.toLowerCase() === search.trim().toLowerCase()
    );

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between font-normal"
          disabled={disabled}
        >
          {displayValue || (
            <span className="text-muted-foreground">{placeholder}</span>
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[300px] p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput
            placeholder="Search suppliers..."
            value={search}
            onValueChange={setSearch}
          />
          <CommandList>
            {isLoading ? (
              <CommandEmpty>Loading...</CommandEmpty>
            ) : suppliers.length === 0 && !showCreateOption ? (
              <CommandEmpty>
                {debouncedSearch
                  ? "No suppliers found. Type to create new."
                  : "Type to search suppliers..."}
              </CommandEmpty>
            ) : (
              <CommandGroup>
                {suppliers.map((supplier) => (
                  <CommandItem
                    key={supplier.id}
                    value={supplier.id}
                    onSelect={() => handleSelect(supplier)}
                  >
                    <Check
                      className={cn(
                        "mr-2 h-4 w-4",
                        value === supplier.id ? "opacity-100" : "opacity-0"
                      )}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="truncate">{supplier.displayName}</div>
                      {supplier.avgLeadTimeDays != null && (
                        <div className="text-xs text-muted-foreground">
                          Avg lead time: {supplier.avgLeadTimeDays.toFixed(0)} days
                        </div>
                      )}
                    </div>
                  </CommandItem>
                ))}
                {showCreateOption && (
                  <CommandItem
                    value={`create-${search}`}
                    onSelect={handleCreateNew}
                    className="text-primary"
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    Create &quot;{search.trim()}&quot;
                  </CommandItem>
                )}
              </CommandGroup>
            )}
          </CommandList>
        </Command>
        {value && (
          <div className="p-2 border-t">
            <Button
              variant="ghost"
              size="sm"
              className="w-full"
              onClick={handleClear}
            >
              Clear selection
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
