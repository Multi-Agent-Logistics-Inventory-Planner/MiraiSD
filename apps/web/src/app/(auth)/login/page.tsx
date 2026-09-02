"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { LoginForm } from "@/components/auth/login-form";
import { Loader2, Moon } from "lucide-react";
import { getSupabaseClient } from "@/lib/supabase";
import { Logo } from "@/components/logo";
import { PitoAsciiArt } from "@/components/auth/pito-ascii-art";
import { Button } from "@/components/ui/button";

function BrandPanel() {
  return (
    <aside className="relative hidden overflow-hidden bg-brand-primary lg:block">
      <PitoAsciiArt />
    </aside>
  );
}

function TemporaryThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();

  return (
    <Button
      type="button"
      variant="outline"
      size="icon-sm"
      className="absolute right-4 top-4 z-20 bg-background/80 backdrop-blur-sm"
      aria-label="Toggle light and dark mode"
      title="Toggle light and dark mode"
      onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
    >
      <Moon />
    </Button>
  );
}

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
      <main className="fixed inset-0 grid h-dvh overflow-hidden bg-background lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <TemporaryThemeToggle />
        <BrandPanel />
        <section className="flex min-h-0 items-center justify-center overflow-y-auto px-6 py-12 sm:px-12 lg:px-16">
          <div className="my-auto flex flex-col items-center justify-center py-12">
            <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
            <p className="mt-4 text-sm text-gray-600">Loading...</p>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="fixed inset-0 grid h-dvh overflow-hidden bg-background lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <TemporaryThemeToggle />
      <BrandPanel />
      <section className="flex min-h-0 items-center justify-center overflow-y-auto px-6 py-12 sm:px-12 lg:px-16">
        <div className="my-auto w-full max-w-sm">
          <header className="mb-6 text-center">
            <div className="mb-3 flex justify-center">
              <Logo width={100} height={56} />
            </div>
            <h1 className="text-2xl font-semibold tracking-tight">Sign in to Mirai Arcade</h1>
          </header>
          <LoginForm />
        </div>
      </section>
    </main>
  );
}
