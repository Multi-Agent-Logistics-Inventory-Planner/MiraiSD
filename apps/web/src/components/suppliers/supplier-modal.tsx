"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import {
  useCreateSupplierMutation,
  useUpdateSupplierMutation,
} from "@/hooks/mutations/use-supplier-mutations";
import type { Supplier } from "@/types/api";

const supplierSchema = z.object({
  displayName: z.string().min(1, "Supplier name is required").max(255),
  contactEmail: z.string().email("Invalid email").optional().or(z.literal("")),
});

type SupplierFormData = z.infer<typeof supplierSchema>;

interface SupplierModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  supplier: Supplier | null;
}

export function SupplierModal({ open, onOpenChange, supplier }: SupplierModalProps) {
  const { toast } = useToast();
  const createMutation = useCreateSupplierMutation();
  const updateMutation = useUpdateSupplierMutation();

  const isEditing = !!supplier;

  const form = useForm<SupplierFormData>({
    resolver: zodResolver(supplierSchema),
    defaultValues: {
      displayName: "",
      contactEmail: "",
    },
  });

  useEffect(() => {
    if (open) {
      if (supplier) {
        form.reset({
          displayName: supplier.displayName,
          contactEmail: supplier.contactEmail || "",
        });
      } else {
        form.reset({
          displayName: "",
          contactEmail: "",
        });
      }
    }
  }, [open, supplier, form]);

  const onSubmit = async (data: SupplierFormData) => {
    try {
      const payload = {
        displayName: data.displayName,
        contactEmail: data.contactEmail || undefined,
      };

      if (isEditing) {
        await updateMutation.mutateAsync({ id: supplier.id, payload });
        toast({ title: "Supplier updated" });
      } else {
        await createMutation.mutateAsync(payload);
        toast({ title: "Supplier created" });
      }

      onOpenChange(false);
    } catch {
      toast({ title: "Error", description: "Failed to save supplier" });
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {isEditing ? "Edit Supplier" : "Add Supplier"}
          </DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="displayName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Supplier Name</FormLabel>
                  <FormControl>
                    <Input placeholder="Acme Corp" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="contactEmail"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Contact Email (optional)</FormLabel>
                  <FormControl>
                    <Input placeholder="orders@example.com" type="email" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? "Saving..." : isEditing ? "Save Changes" : "Add Supplier"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
