import { type SupabaseClient } from "@supabase/supabase-js";
import { createBrowserClient } from "@supabase/ssr";

// Prefer NEXT_PUBLIC_* so values are available in the browser bundle.
// Fallbacks help if you accidentally used the non-prefixed names locally.
const supabaseUrl =
  process.env.NEXT_PUBLIC_SUPABASE_URL ?? process.env.SUPABASE_URL;
const supabaseAnonKey =
  process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ??
  process.env.SUPABASE_ANON_KEY;

let _client: SupabaseClient | null | undefined;

export function getSupabaseClient(): SupabaseClient | null {
  if (_client !== undefined) return _client;
  // This helper is intended for *browser* usage only. Server-side code should
  // use `createSupabaseServerClient()` from `@/lib/supabase/server`.
  if (typeof window === "undefined") {
    _client = null;
    return _client;
  }
  if (!supabaseUrl || !supabaseAnonKey) {
    _client = null;
    return _client;
  }
  // Use cookie-based storage so Next.js Middleware (SSR) can see the session.
  // In the browser runtime, `@supabase/ssr` uses `document.cookie` automatically.
  _client = createBrowserClient(supabaseUrl, supabaseAnonKey);
  return _client;
}

export function getSupabaseEnvStatus() {
  return {
    hasUrl: Boolean(supabaseUrl),
    hasAnonKey: Boolean(supabaseAnonKey),
  };
}

