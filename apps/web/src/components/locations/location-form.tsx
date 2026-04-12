"use client";

import { useEffect } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LOCATION_CODE_PATTERNS, type LocationType, type Location } from "@/types/api";

interface LocationFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  locationType: LocationType;
  initialLocation?: Location | null;
  isSaving?: boolean;
  onSubmit: (payload: { locationCode: string }) => Promise<void> | void;
}

export function LocationForm({
  open,
  onOpenChange,
  locationType,
  initialLocation,
  isSaving,
  onSubmit,
}: LocationFormProps) {
  const schema = z.object({
    code: z
      .string()
      .min(1, "Code is required")
      .refine((v) => LOCATION_CODE_PATTERNS[locationType].test(v), {
        message: `Invalid code format for ${locationType}`,
      }),
  });

  type FormValues = z.infer<typeof schema>;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { code: "" },
  });

  useEffect(() => {
    if (!open) return;
    form.reset({ code: initialLocation?.locationCode ?? "" });
  }, [open, form, initialLocation]);

  async function handleSubmit(values: FormValues) {
    await onSubmit({ locationCode: values.code.trim() });
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{initialLocation ? "Edit Location" : "Add Location"}</DialogTitle>
          <DialogDescription>
            {initialLocation
              ? "Update the location code."
              : "Create a new storage location for this type."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
          <div className="grid gap-2">
            <Label htmlFor="code">Location Code</Label>
            <Input
              id="code"
              placeholder="e.g. B1"
              {...form.register("code")}
            />
            {form.formState.errors.code?.message ? (
              <p className="text-xs text-destructive">{form.formState.errors.code.message}</p>
            ) : (
              <p className="text-xs text-muted-foreground">
                Must match {LOCATION_CODE_PATTERNS[locationType].toString()}
              </p>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" className="dark:bg-accent/50 dark:hover:bg-accent" onClick={() => onOpenChange(false)}>
            
              Cancel
            </Button>
            <Button type="submit" disabled={Boolean(isSaving)} className="text-white bg-brand-primary hover:bg-brand-primary-hover">
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {initialLocation ? "Save" : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
