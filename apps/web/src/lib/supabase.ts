import { createClient, type SupabaseClient } from "@supabase/supabase-js";

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
  if (!supabaseUrl || !supabaseAnonKey) {
    _client = null;
    return _client;
  }
  _client = createClient(supabaseUrl, supabaseAnonKey);
  return _client;
}

export function getSupabaseEnvStatus() {
  return {
    hasUrl: Boolean(supabaseUrl),
    hasAnonKey: Boolean(supabaseAnonKey),
  };
}

