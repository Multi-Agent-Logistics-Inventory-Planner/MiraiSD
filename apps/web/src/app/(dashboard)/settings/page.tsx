"use client";

import { useState, useEffect } from "react";
import { Loader2, Sun, Moon, Monitor } from "lucide-react";
import { useTheme } from "next-themes";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { useAuth } from "@/hooks/use-auth";
import { useToast } from "@/hooks/use-toast";
import { getSupabaseClient } from "@/lib/supabase";
import { updateUser } from "@/lib/api/users";
import { cn } from "@/lib/utils";

const themeOptions = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
] as const;

const navItems = [
  { id: "general", label: "General" },
  { id: "security", label: "Security" },
  { id: "appearance", label: "Appearance" },
] as const;

type SectionId = (typeof navItems)[number]["id"];

function getInitials(name: string | undefined): string {
  if (!name) return "U";
  const parts = name.split(" ");
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}

export default function SettingsPage() {
  const { user, refreshAuth } = useAuth();
  const { toast } = useToast();
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  const [activeSection, setActiveSection] = useState<SectionId>("general");
  const [isResetting, setIsResetting] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");

  useEffect(() => {
    setMounted(true);
  }, []);

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
    <div className="flex flex-col min-h-screen">
      {/* Header */}
      <div className="flex items-center gap-2 p-4 md:p-6 lg:p-8 pb-0">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
      </div>

      {/* Main content */}
      <div className="flex flex-1 flex-col md:flex-row p-4 md:p-6 lg:p-8 gap-8">
        {/* Sidebar navigation */}
        <nav className="md:w-48 shrink-0">
          <ul className="flex md:flex-col gap-1">
            {navItems.map((item) => (
              <li key={item.id}>
                <button
                  onClick={() => setActiveSection(item.id)}
                  className={cn(
                    "w-full text-left px-3 py-2 rounded-md text-sm transition-colors cursor-pointer",
                    activeSection === item.id
                      ? "bg-accent font-medium dark:bg-[#141413]"
                      : "text-muted-foreground hover:text-foreground hover:bg-accent/50 dark:text-foreground dark:hover:bg-[#141413]/35",
                  )}
                >
                  {item.label}
                </button>
              </li>
            ))}
          </ul>
        </nav>

        {/* Content area */}
        <div className="flex-1 max-w-2xl">
          {activeSection === "general" && (
            <div className="space-y-8">
              {/* Profile Section */}
              <section>
                <h2 className="text-lg font-medium mb-6">Profile</h2>

                <div className="space-y-6">
                  {/* Avatar and Name row */}
                  <div className="flex items-start gap-6">
                    <div className="space-y-2">
                      <Label className="text-muted-foreground">Full name</Label>
                      <div className="flex items-center gap-4">
                        <Avatar className="h-12 w-12">
                          <AvatarFallback className="dark:bg-[#363633]">
                            {getInitials(fullName)}
                          </AvatarFallback>
                        </Avatar>
                        <Input
                          value={fullName}
                          onChange={(e) => setFullName(e.target.value)}
                          disabled={isSaving}
                          className="max-w-xs bg-accent/50 border-0"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Email */}
                  <div className="space-y-2">
                    <Label className="text-muted-foreground">Email</Label>
                    <Input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      disabled={isSaving}
                      className="max-w-md bg-accent/50 border-0"
                    />
                  </div>

                  {/* Role */}
                  <div className="space-y-2">
                    <Label className="text-muted-foreground">Role</Label>
                    <p className="text-sm">{user?.role}</p>
                  </div>

                  {/* Save button */}
                  {hasChanges && (
                    <Button
                      onClick={handleSaveProfile}
                      disabled={isSaving}
                      size="sm"
                    >
                      {isSaving ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Saving...
                        </>
                      ) : (
                        "Save changes"
                      )}
                    </Button>
                  )}
                </div>
              </section>
            </div>
          )}

          {activeSection === "security" && (
            <div className="space-y-8">
              <section>
                <h2 className="text-lg font-medium mb-6">Security</h2>

                <div className="space-y-6">
                  {/* Password */}
                  <div className="flex items-center justify-between py-3 border-b">
                    <div>
                      <p className="font-medium">Password</p>
                      <p className="text-sm text-muted-foreground">
                        Send a password reset link to your email
                      </p>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleResetPassword}
                      disabled={isResetting}
                    >
                      {isResetting && (
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      )}
                      Reset password
                    </Button>
                  </div>
                </div>
              </section>
            </div>
          )}

          {activeSection === "appearance" && (
            <div className="space-y-8">
              <section>
                <h2 className="text-lg font-medium mb-6">Appearance</h2>

                <div className="space-y-6">
                  {/* Theme */}
                  <div>
                    <Label className="text-muted-foreground">Theme</Label>
                    <p className="text-sm text-muted-foreground mb-4">
                      Select how the application looks on your device
                    </p>
                    <div className="flex gap-3">
                      {themeOptions.map((option) => {
                        const Icon = option.icon;
                        const isActive = mounted && theme === option.value;
                        return (
                          <button
                            key={option.value}
                            onClick={() => setTheme(option.value)}
                            className={cn(
                              "flex flex-col items-center gap-2 rounded-lg p-4 transition-all cursor-pointer min-w-20",
                              isActive
                                ? "bg-accent ring-1 dark:ring-[#3e3d3a]"
                                : "bg-accent/50 hover:bg-accent",
                            )}
                          >
                            <Icon
                              className={cn(
                                "h-5 w-5",
                                isActive && "text-primary",
                              )}
                            />
                            <span
                              className={cn(
                                "text-sm",
                                isActive && "font-medium",
                              )}
                            >
                              {option.label}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </div>
              </section>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
