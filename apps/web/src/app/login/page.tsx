"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { getSupabaseClient, getSupabaseEnvStatus } from "@/lib/supabase";

export default function LoginPage() {
  const router = useRouter();
  const supabase = getSupabaseClient();
  const envStatus = getSupabaseEnvStatus();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!supabase) {
      setError(
        "Missing Supabase env. Set NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY in apps/web/.env.local, then restart `npm run dev`."
      );
      return;
    }
    setLoading(true);
    try {
      const { error: signInError } = await supabase.auth.signInWithPassword({
        email,
        password,
      });
      if (signInError) {
        setError(signInError.message);
        return;
      }
      router.push("/dashboard");
      router.refresh();
    } finally {
      setLoading(false);
    }
  }

  return (
    <main>
      <h1>Login</h1>
      {!envStatus.hasUrl || !envStatus.hasAnonKey ? (
        <p>
          Supabase env missing. Create <code>apps/web/.env.local</code> with{" "}
          <code>NEXT_PUBLIC_SUPABASE_URL</code> and{" "}
          <code>NEXT_PUBLIC_SUPABASE_ANON_KEY</code>, then restart{" "}
          <code>npm run dev</code>.
        </p>
      ) : null}
      <form onSubmit={onSubmit}>
        <div>
          <label>
            Email{" "}
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </label>
        </div>
        <div>
          <label>
            Password{" "}
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
          </label>
        </div>
        <button type="submit" disabled={loading || !supabase}>
          {loading ? "Signing in..." : "Sign in"}
        </button>
      </form>

      {error ? (
        <pre style={{ color: "crimson" }}>{error}</pre>
      ) : (
        <p style={{ opacity: 0.8 }}>
          If you don’t have a user yet, create one in Supabase Auth → Users.
        </p>
      )}
    </main>
  );
}

