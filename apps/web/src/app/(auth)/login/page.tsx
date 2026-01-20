"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { LoginForm } from "@/components/auth/login-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Loader2 } from "lucide-react";
import { getSupabaseClient } from "@/lib/supabase";
import { Logo } from "@/components/logo";

export default function LoginPage() {
  const router = useRouter();
  const [isCheckingInvite, setIsCheckingInvite] = useState(true);
  const supabase = getSupabaseClient();

  useEffect(() => {
    const handleInviteRedirect = async () => {
      // Check if this is an invite callback (hash contains type=invite)
      const hash = window.location.hash;

      if (hash && hash.includes("type=invite")) {
        // Parse hash parameters
        const hashParams = new URLSearchParams(hash.substring(1));
        const accessToken = hashParams.get("access_token");
        const refreshToken = hashParams.get("refresh_token");

        if (accessToken && refreshToken && supabase) {
          // Set the session from the tokens
          const { error } = await supabase.auth.setSession({
            access_token: accessToken,
            refresh_token: refreshToken,
          });

          if (!error) {
            // Redirect to onboarding page
            router.replace("/auth/accept-invite?from_invite=true");
            return;
          }
        }
      }

      setIsCheckingInvite(false);
    };

    handleInviteRedirect();
  }, [router, supabase]);

  if (isCheckingInvite) {
    return (
      <Card className="shadow-lg">
        <CardContent className="flex flex-col items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
          <p className="mt-4 text-sm text-gray-600">Loading...</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="shadow-lg">
      <CardHeader className="space-y-1 text-center">
        <div className="flex justify-center mb-4">
          <Logo width={120} height={120} />
        </div>
        <CardTitle className="text-2xl">Welcome back</CardTitle>
        <CardDescription>
          Sign in to your account to continue
        </CardDescription>
      </CardHeader>
      <CardContent>
        <LoginForm />
      </CardContent>
    </Card>
  );
}
