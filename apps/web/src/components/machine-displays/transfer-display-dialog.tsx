"use client";

import { useEffect, useMemo, useState } from "react";
import { ArrowLeftRight, Check, ChevronRight, Loader2, Monitor } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import {
  LocationType,
  MachineDisplay,
  StorageLocation,
  SingleClawMachine,
  DoubleClawMachine,
  KeychainMachine,
  FourCornerMachine,
  PusherMachine,
} from "@/types/api";
import { useLocationsOnly } from "@/hooks/queries/use-locations";
import {
  useActiveDisplaysForMachine,
  useActiveDisplaysByType,
} from "@/hooks/queries/use-machine-displays";

interface TransferDisplayDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  locationType: LocationType;
  currentMachineId: string;
  currentMachineCode: string;
  currentDisplays: MachineDisplay[];
  actorId?: string;
  onTransfer: (
    itemsToSend: MachineDisplay[],
    itemsToReceive: MachineDisplay[],
    targetMachineId: string
  ) => Promise<void>;
  isSubmitting?: boolean;
}

type Step = "select-machine" | "select-items";

function getMachineCode(locationType: LocationType, loc: StorageLocation): string {
  switch (locationType) {
    case LocationType.SINGLE_CLAW_MACHINE:
      return (loc as SingleClawMachine).singleClawMachineCode;
    case LocationType.DOUBLE_CLAW_MACHINE:
      return (loc as DoubleClawMachine).doubleClawMachineCode;
    case LocationType.KEYCHAIN_MACHINE:
      return (loc as KeychainMachine).keychainMachineCode;
    case LocationType.FOUR_CORNER_MACHINE:
      return (loc as FourCornerMachine).fourCornerMachineCode;
    case LocationType.PUSHER_MACHINE:
      return (loc as PusherMachine).pusherMachineCode;
    default:
      return loc.id;
  }
}

interface DisplayItemProps {
  display: MachineDisplay;
  selected: boolean;
  onToggle: () => void;
  disabled?: boolean;
  direction: "send" | "receive";
}

function DisplayItem({ display, selected, onToggle, disabled, direction }: DisplayItemProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      className={cn(
        "w-full flex items-center gap-3 p-3 rounded-lg border transition-colors text-left",
        selected
          ? direction === "send"
            ? "bg-orange-50 border-orange-300 dark:bg-orange-950/30 dark:border-orange-700"
            : "bg-green-50 border-green-300 dark:bg-green-950/30 dark:border-green-700"
          : "bg-background hover:bg-muted/50 border-border",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="relative h-10 w-10 shrink-0 rounded-md overflow-hidden bg-muted flex items-center justify-center">
        <Monitor className="h-4 w-4 text-muted-foreground" />
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium truncate">{display.productName}</p>
        <p className="text-xs text-muted-foreground">{display.productSku}</p>
      </div>

      <div
        className={cn(
          "h-5 w-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-colors",
          selected
            ? direction === "send"
              ? "bg-orange-500 border-orange-500"
              : "bg-green-500 border-green-500"
            : "border-muted-foreground/30"
        )}
      >
        {selected && <Check className="h-3 w-3 text-white" />}
      </div>
    </button>
  );
}

interface MachineCardProps {
  machine: StorageLocation;
  locationType: LocationType;
  selected: boolean;
  onSelect: () => void;
  disabled?: boolean;
  displayCount: number;
}

function MachineCard({
  machine,
  locationType,
  selected,
  onSelect,
  disabled,
  displayCount,
}: MachineCardProps) {
  const code = getMachineCode(locationType, machine);

  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={disabled}
      className={cn(
        "w-full flex items-center gap-3 p-4 rounded-lg border transition-colors text-left",
        selected
          ? "bg-primary/5 border-primary"
          : "bg-background hover:bg-muted/50 border-border",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="flex-1 min-w-0">
        <p className="font-medium">{code}</p>
        <p className="text-sm text-muted-foreground">
          {displayCount} product{displayCount !== 1 ? "s" : ""} on display
        </p>
      </div>

      {selected ? (
        <div className="h-6 w-6 rounded-full bg-primary flex items-center justify-center shrink-0">
          <Check className="h-4 w-4 text-primary-foreground" />
        </div>
      ) : (
        <ChevronRight className="h-5 w-5 text-muted-foreground shrink-0" />
      )}
    </button>
  );
}

export function TransferDisplayDialog({
  open,
  onOpenChange,
  locationType,
  currentMachineId,
  currentMachineCode,
  currentDisplays,
  onTransfer,
  isSubmitting = false,
}: TransferDisplayDialogProps) {
  const [step, setStep] = useState<Step>("select-machine");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedMachineId, setSelectedMachineId] = useState<string | null>(null);
  const [itemsToSend, setItemsToSend] = useState<Set<string>>(new Set());
  const [itemsToReceive, setItemsToReceive] = useState<Set<string>>(new Set());

  // Fetch all machines of the same type
  const { data: machines = [], isLoading: isMachinesLoading } = useLocationsOnly(locationType);

  // Fetch displays for the selected target machine
  const { data: targetDisplays = [], isLoading: isTargetDisplaysLoading } =
    useActiveDisplaysForMachine(
      selectedMachineId ? locationType : undefined,
      selectedMachineId ?? undefined
    );

  // Get display counts for each machine
  const { data: allDisplaysByType = [] } = useActiveDisplaysByType(locationType);

  // Create a map of machine ID to display count
  const displayCountByMachine = useMemo(() => {
    const map = new Map<string, number>();
    for (const display of allDisplaysByType) {
      const count = map.get(display.machineId) ?? 0;
      map.set(display.machineId, count + 1);
    }
    // Also include current machine's displays
    map.set(currentMachineId, currentDisplays.length);
    return map;
  }, [allDisplaysByType, currentMachineId, currentDisplays.length]);

  // Filter machines (exclude current machine)
  const availableMachines = useMemo(() => {
    return machines
      .filter((m) => m.id !== currentMachineId)
      .filter((m) => {
        if (!searchQuery.trim()) return true;
        const code = getMachineCode(locationType, m).toLowerCase();
        return code.includes(searchQuery.toLowerCase().trim());
      });
  }, [machines, currentMachineId, searchQuery, locationType]);

  const selectedMachine = machines.find((m) => m.id === selectedMachineId);
  const selectedMachineCode = selectedMachine
    ? getMachineCode(locationType, selectedMachine)
    : "";

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setStep("select-machine");
      setSearchQuery("");
      setSelectedMachineId(null);
      setItemsToSend(new Set());
      setItemsToReceive(new Set());
    }
  }, [open]);

  function handleMachineSelect(machineId: string) {
    setSelectedMachineId(machineId);
    setStep("select-items");
  }

  function handleBack() {
    setStep("select-machine");
    setSelectedMachineId(null);
    setItemsToSend(new Set());
    setItemsToReceive(new Set());
  }

  function toggleItemToSend(displayId: string) {
    setItemsToSend((prev) => {
      const next = new Set(prev);
      if (next.has(displayId)) {
        next.delete(displayId);
      } else {
        next.add(displayId);
      }
      return next;
    });
  }

  function toggleItemToReceive(displayId: string) {
    setItemsToReceive((prev) => {
      const next = new Set(prev);
      if (next.has(displayId)) {
        next.delete(displayId);
      } else {
        next.add(displayId);
      }
      return next;
    });
  }

  async function handleTransfer() {
    if (!selectedMachineId) return;

    const sending = currentDisplays.filter((d) => itemsToSend.has(d.id));
    const receiving = targetDisplays.filter((d) => itemsToReceive.has(d.id));

    if (sending.length === 0 && receiving.length === 0) return;

    await onTransfer(sending, receiving, selectedMachineId);
  }

  const hasSelections = itemsToSend.size > 0 || itemsToReceive.size > 0;
  const canTransfer = hasSelections && !isSubmitting;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl h-[85dvh] max-h-[85dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>
            {step === "select-machine" ? "Transfer Display" : "Select Items to Transfer"}
          </DialogTitle>
          <DialogDescription>
            {step === "select-machine"
              ? "Select another machine to transfer display items with."
              : `Trade display items between ${currentMachineCode} and ${selectedMachineCode}.`}
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col px-6 py-4">
          {step === "select-machine" ? (
            <>
              <div className="shrink-0 mb-3">
                <Label className="text-xs text-muted-foreground mb-2 block">
                  Select a machine ({availableMachines.length})
                </Label>
                <Input
                  type="text"
                  placeholder="Search machines..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="h-9"
                />
              </div>

              {isMachinesLoading ? (
                <div className="flex-1 flex items-center justify-center">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : availableMachines.length === 0 ? (
                <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                  {searchQuery
                    ? "No machines match your search"
                    : "No other machines available"}
                </div>
              ) : (
                <ScrollArea className="flex-1">
                  <div className="space-y-2 pr-3 pb-2">
                    {availableMachines.map((machine) => (
                      <MachineCard
                        key={machine.id}
                        machine={machine}
                        locationType={locationType}
                        selected={selectedMachineId === machine.id}
                        onSelect={() => handleMachineSelect(machine.id)}
                        displayCount={displayCountByMachine.get(machine.id) ?? 0}
                      />
                    ))}
                  </div>
                </ScrollArea>
              )}
            </>
          ) : (
            <>
              {/* Two-column layout for item selection */}
              <div className="flex-1 min-h-0 grid grid-cols-2 gap-4">
                {/* Left column: Current machine items to send */}
                <div className="flex flex-col min-h-0">
                  <div className="shrink-0 mb-2">
                    <Label className="text-xs text-muted-foreground">
                      From {currentMachineCode}
                    </Label>
                    <p className="text-xs text-orange-600 dark:text-orange-400">
                      {itemsToSend.size > 0
                        ? `Sending ${itemsToSend.size} item${itemsToSend.size !== 1 ? "s" : ""}`
                        : "Select items to send"}
                    </p>
                  </div>

                  {currentDisplays.length === 0 ? (
                    <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                      No items on display
                    </div>
                  ) : (
                    <ScrollArea className="flex-1">
                      <div className="space-y-2 pr-2 pb-2">
                        {currentDisplays.map((display) => (
                          <DisplayItem
                            key={display.id}
                            display={display}
                            selected={itemsToSend.has(display.id)}
                            onToggle={() => toggleItemToSend(display.id)}
                            direction="send"
                            disabled={isSubmitting}
                          />
                        ))}
                      </div>
                    </ScrollArea>
                  )}
                </div>

                {/* Center arrow */}
                <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 hidden sm:flex">
                  <div className="bg-background p-2 rounded-full border">
                    <ArrowLeftRight className="h-4 w-4 text-muted-foreground" />
                  </div>
                </div>

                {/* Right column: Target machine items to receive */}
                <div className="flex flex-col min-h-0">
                  <div className="shrink-0 mb-2">
                    <Label className="text-xs text-muted-foreground">
                      From {selectedMachineCode}
                    </Label>
                    <p className="text-xs text-green-600 dark:text-green-400">
                      {itemsToReceive.size > 0
                        ? `Receiving ${itemsToReceive.size} item${itemsToReceive.size !== 1 ? "s" : ""}`
                        : "Select items to receive"}
                    </p>
                  </div>

                  {isTargetDisplaysLoading ? (
                    <div className="flex-1 flex items-center justify-center">
                      <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                    </div>
                  ) : targetDisplays.length === 0 ? (
                    <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                      No items on display
                    </div>
                  ) : (
                    <ScrollArea className="flex-1">
                      <div className="space-y-2 pr-2 pb-2">
                        {targetDisplays.map((display) => (
                          <DisplayItem
                            key={display.id}
                            display={display}
                            selected={itemsToReceive.has(display.id)}
                            onToggle={() => toggleItemToReceive(display.id)}
                            direction="receive"
                            disabled={isSubmitting}
                          />
                        ))}
                      </div>
                    </ScrollArea>
                  )}
                </div>
              </div>

              {/* Summary */}
              {hasSelections && (
                <div className="shrink-0 mt-3 pt-3 border-t">
                  <div className="flex items-center gap-4 text-sm">
                    {itemsToSend.size > 0 && (
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="secondary"
                          className="bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300"
                        >
                          {itemsToSend.size}
                        </Badge>
                        <span className="text-muted-foreground">
                          to {selectedMachineCode}
                        </span>
                      </div>
                    )}
                    {itemsToSend.size > 0 && itemsToReceive.size > 0 && (
                      <ArrowLeftRight className="h-4 w-4 text-muted-foreground" />
                    )}
                    {itemsToReceive.size > 0 && (
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="secondary"
                          className="bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300"
                        >
                          {itemsToReceive.size}
                        </Badge>
                        <span className="text-muted-foreground">
                          to {currentMachineCode}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <DialogFooter className="shrink-0 border-t p-6">
          {step === "select-items" && (
            <Button
              type="button"
              variant="ghost"
              onClick={handleBack}
              disabled={isSubmitting}
              className="mr-auto"
            >
              Back
            </Button>
          )}
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          {step === "select-items" && (
            <Button type="button" onClick={handleTransfer} disabled={!canTransfer}>
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Transferring...
                </>
              ) : (
                "Transfer"
              )}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
