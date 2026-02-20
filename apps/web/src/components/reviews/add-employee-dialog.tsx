"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { createReviewEmployee } from "@/lib/api/reviews";

interface AddEmployeeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function AddEmployeeDialog({
  open,
  onOpenChange,
  onSuccess,
}: AddEmployeeDialogProps) {
  const [canonicalName, setCanonicalName] = useState("");
  const [nameVariantsText, setNameVariantsText] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const resetForm = () => {
    setCanonicalName("");
    setNameVariantsText("");
    setError(null);
    setSuccess(false);
  };

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen);
    if (!nextOpen) {
      resetForm();
    }
  };

  const handleSubmit = async () => {
    if (!canonicalName.trim()) {
      setError("Employee name is required");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const nameVariants = nameVariantsText
        .split("\n")
        .map((v) => v.trim())
        .filter((v) => v.length > 0);

      await createReviewEmployee({
        canonicalName: canonicalName.trim(),
        nameVariants,
      });

      setSuccess(true);
      setTimeout(() => {
        handleOpenChange(false);
        onSuccess();
      }, 1500);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to add employee"
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Employee to Review Tracker</DialogTitle>
          <DialogDescription>
            Add an employee to track their reviews. Name variants help match
            reviews with different spellings of their name.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="canonical-name">Employee Name</Label>
            <Input
              id="canonical-name"
              placeholder="John Smith"
              value={canonicalName}
              onChange={(e) => setCanonicalName(e.target.value)}
              disabled={isSubmitting}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="name-variants">
              Name Variants{" "}
              <span className="text-muted-foreground font-normal">
                (optional, one per line)
              </span>
            </Label>
            <Textarea
              id="name-variants"
              placeholder="Johnny Smith&#10;J. Smith&#10;John S."
              value={nameVariantsText}
              onChange={(e) => setNameVariantsText(e.target.value)}
              disabled={isSubmitting}
              rows={3}
            />
          </div>

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {success && (
            <Alert className="bg-green-50 border-green-200 text-green-800">
              <AlertDescription>Employee added successfully!</AlertDescription>
            </Alert>
          )}
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={isSubmitting || success}>
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Adding...
              </>
            ) : (
              "Add Employee"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
