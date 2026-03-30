"use client";

import { useState, useEffect } from "react";
import {
  Archive,
  Box,
  ChevronsRight,
  CircleHelp,
  Disc3,
  Gamepad,
  Gamepad2,
  Key,
  Layers,
  LayoutGrid,
  Loader2,
  PanelsTopLeft,
  Package,
  Warehouse,
  ShoppingBag,
  Boxes,
  type LucideIcon,
} from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import {
  useStorageLocations,
  useCreateStorageLocationMutation,
} from "@/hooks/queries/use-storage-locations";
import { LOCATION_TAB_CONFIG, type LocationTabConfig } from "./location-tabs";

// Available icons for custom storage locations
const ICON_OPTIONS: { name: string; icon: LucideIcon; label: string }[] = [
  { name: "Box", icon: Box, label: "Box" },
  { name: "Archive", icon: Archive, label: "Archive" },
  { name: "Layers", icon: Layers, label: "Layers" },
  { name: "Package", icon: Package, label: "Package" },
  { name: "Warehouse", icon: Warehouse, label: "Warehouse" },
  { name: "Boxes", icon: Boxes, label: "Boxes" },
  { name: "ShoppingBag", icon: ShoppingBag, label: "Shopping Bag" },
  { name: "Gamepad", icon: Gamepad, label: "Gamepad" },
  { name: "Gamepad2", icon: Gamepad2, label: "Gamepad 2" },
  { name: "Disc3", icon: Disc3, label: "Disc" },
  { name: "Key", icon: Key, label: "Key" },
  { name: "LayoutGrid", icon: LayoutGrid, label: "Grid" },
  { name: "ChevronsRight", icon: ChevronsRight, label: "Chevrons" },
  { name: "PanelsTopLeft", icon: PanelsTopLeft, label: "Panels" },
  { name: "CircleHelp", icon: CircleHelp, label: "Help" },
];

interface AddStorageLocationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  missingPresets: LocationTabConfig[];
}

export function AddStorageLocationDialog({
  open,
  onOpenChange,
  missingPresets,
}: AddStorageLocationDialogProps) {
  const { toast } = useToast();
  const { data: storageLocations } = useStorageLocations();
  const createMutation = useCreateStorageLocationMutation();

  // Form state
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [codePrefix, setCodePrefix] = useState("");
  const [icon, setIcon] = useState("Box");
  const [hasDisplay, setHasDisplay] = useState(false);
  const [isDisplayOnly, setIsDisplayOnly] = useState(false);

  // Auto-generate code from name
  useEffect(() => {
    if (name) {
      const generatedCode = name.toUpperCase().replace(/[^A-Z0-9]/g, "_");
      setCode(generatedCode);
      // Auto-generate prefix from first letters
      const words = name.split(/\s+/);
      const prefix = words.map((w) => w[0]?.toUpperCase() || "").join("");
      setCodePrefix(prefix.slice(0, 3));
    }
  }, [name]);

  const resetForm = () => {
    setName("");
    setCode("");
    setCodePrefix("");
    setIcon("Box");
    setHasDisplay(false);
    setIsDisplayOnly(false);
  };

  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen) {
      resetForm();
    }
    onOpenChange(newOpen);
  };

  // Calculate next display order
  const getNextDisplayOrder = () => {
    if (!storageLocations?.length) return 0;
    return Math.max(...storageLocations.map((sl) => sl.displayOrder)) + 1;
  };

  // Handle preset creation
  const handlePresetCreate = async (config: LocationTabConfig) => {
    try {
      await createMutation.mutateAsync({
        code: config.code,
        name: config.label,
        codePrefix: config.codePrefix,
        icon: config.icon.displayName || config.icon.name || "Box",
        hasDisplay: config.hasDisplay,
        isDisplayOnly: config.isDisplayOnly,
        displayOrder: config.displayOrder,
      });
      toast({ title: `Created ${config.label} storage location` });
      handleOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to create storage location";
      toast({ title: "Error", description: message, variant: "destructive" });
    }
  };

  // Handle custom creation
  const handleCustomSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim()) {
      toast({ title: "Error", description: "Name is required", variant: "destructive" });
      return;
    }
    if (!code.trim()) {
      toast({ title: "Error", description: "Code is required", variant: "destructive" });
      return;
    }

    try {
      await createMutation.mutateAsync({
        code: code.trim(),
        name: name.trim(),
        codePrefix: codePrefix.trim() || undefined,
        icon,
        hasDisplay,
        isDisplayOnly: hasDisplay ? isDisplayOnly : false,
        displayOrder: getNextDisplayOrder(),
      });
      toast({ title: `Created ${name} storage location` });
      resetForm();
      handleOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to create storage location";
      toast({ title: "Error", description: message, variant: "destructive" });
    }
  };

  const SelectedIcon = ICON_OPTIONS.find((i) => i.name === icon)?.icon || Box;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Add Storage Location</DialogTitle>
          <DialogDescription>
            Create a new storage location type.
          </DialogDescription>
        </DialogHeader>

        {/* Presets Section */}
        {missingPresets.length > 0 && (
          <div className="space-y-2">
            <Label className="text-xs text-muted-foreground">Quick Add Preset</Label>
            <div className="flex flex-wrap gap-2">
              {missingPresets.slice(0, 6).map((config) => (
                <Button
                  key={config.code}
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => handlePresetCreate(config)}
                  disabled={createMutation.isPending}
                  className="gap-1.5"
                >
                  <config.icon className="h-3.5 w-3.5" />
                  {config.label}
                </Button>
              ))}
            </div>
          </div>
        )}

        {/* Divider */}
        {missingPresets.length > 0 && (
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-background px-2 text-muted-foreground">or create custom</span>
            </div>
          </div>
        )}

        {/* Custom Form */}
        <form onSubmit={handleCustomSubmit} className="space-y-4">
          <div className="grid gap-4">
            {/* Name */}
            <div className="grid gap-2">
              <Label htmlFor="sl-name">Name</Label>
              <Input
                id="sl-name"
                placeholder="e.g., Shelves"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>

            {/* Code and Prefix */}
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="sl-code">Code</Label>
                <Input
                  id="sl-code"
                  placeholder="e.g., SHELVES"
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="sl-prefix">Code Prefix</Label>
                <Input
                  id="sl-prefix"
                  placeholder="e.g., SH"
                  value={codePrefix}
                  onChange={(e) => setCodePrefix(e.target.value.toUpperCase())}
                  maxLength={3}
                />
              </div>
            </div>

            {/* Icon */}
            <div className="grid gap-2">
              <Label>Icon</Label>
              <Select value={icon} onValueChange={setIcon}>
                <SelectTrigger>
                  <SelectValue>
                    <div className="flex items-center gap-2">
                      <SelectedIcon className="h-4 w-4" />
                      {ICON_OPTIONS.find((i) => i.name === icon)?.label || icon}
                    </div>
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {ICON_OPTIONS.map(({ name: iconName, icon: IconComp, label }) => (
                    <SelectItem key={iconName} value={iconName}>
                      <div className="flex items-center gap-2">
                        <IconComp className="h-4 w-4" />
                        {label}
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Display Options */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="has-display">Has Display</Label>
                  <p className="text-xs text-muted-foreground">
                    Items can be displayed to customers
                  </p>
                </div>
                <Switch
                  id="has-display"
                  checked={hasDisplay}
                  onCheckedChange={setHasDisplay}
                />
              </div>

              {hasDisplay && (
                <div className="flex items-center justify-between pl-4 border-l-2">
                  <div className="space-y-0.5">
                    <Label htmlFor="display-only">Display Only</Label>
                    <p className="text-xs text-muted-foreground">
                      No storage, only for display tracking
                    </p>
                  </div>
                  <Switch
                    id="display-only"
                    checked={isDisplayOnly}
                    onCheckedChange={setIsDisplayOnly}
                  />
                </div>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
