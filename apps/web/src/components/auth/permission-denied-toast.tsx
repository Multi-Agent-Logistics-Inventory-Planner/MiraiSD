"use client";

import { useEffect } from "react";
import { useToast } from "@/hooks/use-toast";

/**
 * Component that checks for permission_denied cookie and shows error toast.
 * Used by middleware to notify users when they're redirected due to lacking permissions.
 *
 * The middleware sets a short-lived cookie when redirecting users who lack
 * permissions to access a route. This component reads that cookie and displays
 * an error toast, then clears the cookie.
 */
export function PermissionDeniedToast() {
  const { toast } = useToast();

  useEffect(() => {
    // Check if permission_denied cookie exists
    const cookies = document.cookie.split(";");
    const permissionDeniedCookie = cookies.find((cookie) =>
      cookie.trim().startsWith("permission_denied=")
    );

    if (permissionDeniedCookie) {
      // Show error toast
      toast({
        title: "Access Denied",
        description: "You don't have permission to access that page.",
        variant: "destructive",
      });

      // Clear the cookie immediately after showing toast
      document.cookie =
        "permission_denied=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    }
  }, [toast]);

  // This component renders nothing - it only shows toasts
  return null;
}
