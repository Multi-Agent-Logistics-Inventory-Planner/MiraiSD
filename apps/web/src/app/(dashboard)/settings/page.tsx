"use client";

import { useState, useEffect } from "react";
import { Loader2 } from "lucide-react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/hooks/use-auth";
import { useToast } from "@/hooks/use-toast";
import { getSupabaseClient } from "@/lib/supabase";
import { updateUser } from "@/lib/api/users";

export default function SettingsPage() {
  const { user, refreshAuth } = useAuth();
  const { toast } = useToast();
  const [isResetting, setIsResetting] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");

  useEffect(() => {
    if (user) {
      setFullName(user.personName || "");
      setEmail(user.email || "");
    }
  }, [user]);

  async function handleSaveProfile() {
    if (!user?.personId) return;
    if (!fullName || !email) {
      toast({
        title: "Validation error",
        description: "Name and email are required.",
        variant: "destructive",
      });
      return;
    }

    setIsSaving(true);

    try {
      await updateUser(user.personId, {
        fullName,
        email,
        role: user.role,
      });
      await refreshAuth();
      toast({
        title: "Profile updated",
        description: "Your profile has been saved.",
      });
    } catch (err) {
      toast({
        title: "Failed to update profile",
        description: err instanceof Error ? err.message : "An error occurred",
        variant: "destructive",
      });
    } finally {
      setIsSaving(false);
    }
  }

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

  const hasChanges =
    fullName !== (user?.personName || "") || email !== (user?.email || "");

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
      </div>
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-2">
              <Label htmlFor="settings-name">Full Name</Label>
              <Input
                id="settings-name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                disabled={isSaving}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="settings-email">Email</Label>
              <Input
                id="settings-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isSaving}
              />
            </div>
            <p className="text-sm text-muted-foreground">Role: {user?.role}</p>
            <Button
              onClick={handleSaveProfile}
              disabled={isSaving || !hasChanges}
            >
              {isSaving ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                "Save Changes"
              )}
            </Button>
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
    </div>
  );
}
