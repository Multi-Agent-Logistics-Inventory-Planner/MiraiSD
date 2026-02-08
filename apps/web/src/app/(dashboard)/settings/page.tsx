"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { DashboardHeader } from "@/components/dashboard-header";
import { useAuth } from "@/hooks/use-auth";
import { useToast } from "@/hooks/use-toast";
import { getSupabaseClient } from "@/lib/supabase";

export default function SettingsPage() {
  const { user } = useAuth();
  const { toast } = useToast();
  const [isResetting, setIsResetting] = useState(false);

  async function handleResetPassword() {
    if (!user?.email) return;

    setIsResetting(true);

    try {
      const supabase = getSupabaseClient();
      if (!supabase) {
        throw new Error("Supabase client not configured");
      }

      const { error } = await supabase.auth.resetPasswordForEmail(user.email, {
        redirectTo: `${window.location.origin}/reset-password`,
      });

      if (error) {
        throw new Error(error.message);
      }

      toast({
        title: "Password reset email sent",
        description: "Please check your inbox for the reset link.",
      });
    } catch (err) {
      toast({
        title: "Failed to send reset email",
        description: err instanceof Error ? err.message : "An error occurred",
        variant: "destructive",
      });
    } finally {
      setIsResetting(false);
    }
  }

  return (
    <div className="flex flex-col">
      <DashboardHeader title="Settings" />
      <main className="flex-1 p-4 md:p-8 space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-xl font-bold">{user?.personName || "—"}</p>
            <p className="text-muted-foreground">{user?.email}</p>
            <p className="text-muted-foreground">Role: {user?.role}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Security</CardTitle>
          </CardHeader>
          <CardContent>
            <Button onClick={handleResetPassword} disabled={isResetting}>
              {isResetting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Reset Password
            </Button>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
