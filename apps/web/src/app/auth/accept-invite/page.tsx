"use client";

import { useState, useEffect, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { getSupabaseClient } from "@/lib/supabase";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Loader2 } from "lucide-react";

function AcceptInviteContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const supabase = getSupabaseClient();

  const [isLoading, setIsLoading] = useState(false);
  const [isVerifying, setIsVerifying] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState<string | null>(null);

  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  useEffect(() => {
    if (!supabase) {
      setError("Supabase client not configured");
      setIsVerifying(false);
      return;
    }

    const processAuth = async () => {
      // First check if already have a session
      const { data: sessionData } = await supabase.auth.getSession();

      if (sessionData.session?.user?.email) {
        setEmail(sessionData.session.user.email);
        setIsVerifying(false);
        return;
      }

      // Check URL fragment for tokens (Supabase puts tokens in fragment)
      const hash = window.location.hash.substring(1);
      if (hash) {
        const hashParams = new URLSearchParams(hash);
        const accessToken = hashParams.get("access_token");
        const refreshToken = hashParams.get("refresh_token");
        const typeFromHash = hashParams.get("type");

        // Valid types: invite (new user) or magiclink (existing user resend)
        const isValidType = typeFromHash === "invite" || typeFromHash === "magiclink";

        if (accessToken && refreshToken && isValidType) {
          // Manually set the session from fragment tokens
          const { data, error: sessionError } = await supabase.auth.setSession({
            access_token: accessToken,
            refresh_token: refreshToken,
          });

          if (sessionError) {
            setError(sessionError.message);
            setIsVerifying(false);
            return;
          }

          if (data.user?.email) {
            setEmail(data.user.email);
            // Clear the hash from URL for cleaner look
            window.history.replaceState(null, "", window.location.pathname);
            setIsVerifying(false);
            return;
          }
        }
      }

      // Check query params as fallback (old flow with token_hash)
      const tokenHash = searchParams.get("token_hash");
      const type = searchParams.get("type");

      if (tokenHash && (type === "invite" || type === "magiclink")) {
        try {
          const { data, error: verifyError } = await supabase.auth.verifyOtp({
            token_hash: tokenHash,
            type: type as "invite" | "magiclink",
          });

          if (verifyError) {
            setError(verifyError.message);
            setIsVerifying(false);
            return;
          }

          if (data.user?.email) {
            setEmail(data.user.email);
          }
          setIsVerifying(false);
          return;
        } catch (err) {
          setError("Failed to verify invitation");
          setIsVerifying(false);
          return;
        }
      }

      // No valid tokens found
      setError("Invalid invitation link");
      setIsVerifying(false);
    };

    processAuth();
  }, [searchParams, supabase]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!supabase) {
      setError("Supabase client not configured");
      return;
    }

    if (!fullName.trim()) {
      setError("Full name is required");
      return;
    }

    if (password.length < 6) {
      setError("Password must be at least 6 characters");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    setIsLoading(true);

    try {
      const { error: updateError } = await supabase.auth.updateUser({
        password: password,
        data: { name: fullName },
      });

      if (updateError) {
        setError(updateError.message);
        setIsLoading(false);
        return;
      }

      // Refresh session to get new JWT with updated user_metadata
      await supabase.auth.refreshSession();

      const { data: sessionData } = await supabase.auth.getSession();
      const accessToken = sessionData.session?.access_token;

      if (accessToken) {
        const backendUrl =
          process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:4000";
        await fetch(`${backendUrl}/api/auth/sync-user`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
        });
      }

      // Redirect based on role - employees go to storage, admins go to dashboard
      const rawRole = sessionData.session?.user?.user_metadata?.role as string | undefined;
      const userRole = rawRole?.toUpperCase();
      if (userRole === "EMPLOYEE") {
        router.push("/storage");
      } else {
        router.push("/");
      }
    } catch (err) {
      setError("Failed to complete profile setup");
      setIsLoading(false);
    }
  };

  if (isVerifying) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-linear-to-br from-slate-50 to-slate-100 dark:from-[#262624] dark:to-[#262624] p-4">
        <Card className="w-full max-w-md shadow-lg">
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
            <p className="mt-4 text-sm text-muted-foreground">
              Verifying invitation...
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  if (error && !email) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-linear-to-br from-slate-50 to-slate-100 dark:from-[#262624] dark:to-[#262624] p-4">
        <Card className="w-full max-w-md shadow-lg">
          <CardHeader>
            <CardTitle className="text-red-600">Invalid Invitation</CardTitle>
            <CardDescription>
              This invitation link is invalid or has expired.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-linear-to-br from-slate-50 to-slate-100 dark:from-[#262624] dark:to-[#262624] p-4">
      <Card className="w-full max-w-md shadow-lg">
        <CardHeader>
          <CardTitle>Complete Your Profile</CardTitle>
          <CardDescription>
            You've been invited to join. Please complete your profile to
            continue.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            {email && (
              <div className="grid gap-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  disabled
                  className="bg-muted dark:bg-[#1c1c1c] placeholder:font-light"
                />
              </div>
            )}

            <div className="grid gap-2">
              <Label htmlFor="fullName">Full Name</Label>
              <Input
                id="fullName"
                type="text"
                placeholder="Enter your full name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                disabled={isLoading}
                required
                className="dark:bg-[#1c1c1c] placeholder:font-light"
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                placeholder="Create a password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isLoading}
                required
                minLength={6}
                className="dark:bg-[#1c1c1c] placeholder:font-light"
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="confirmPassword">Confirm Password</Label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="Confirm your password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                disabled={isLoading}
                required
                className="dark:bg-[#1c1c1c] placeholder:font-light"
              />
            </div>

            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Setting up...
                </>
              ) : (
                "Complete Setup"
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function LoadingFallback() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-linear-to-br from-slate-50 to-slate-100 dark:from-[#262624] dark:to-[#262624] p-4">
      <Card className="w-full max-w-md shadow-lg">
        <CardContent className="flex flex-col items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
          <p className="mt-4 text-sm text-muted-foreground">Loading...</p>
        </CardContent>
      </Card>
    </div>
  );
}

export default function AcceptInvitePage() {
  return (
    <Suspense fallback={<LoadingFallback />}>
      <AcceptInviteContent />
    </Suspense>
  );
}
