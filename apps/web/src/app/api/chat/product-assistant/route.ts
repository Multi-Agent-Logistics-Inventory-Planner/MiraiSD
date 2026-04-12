import { google } from "@ai-sdk/google";
import {
  convertToModelMessages,
  stepCountIs,
  streamText,
  type UIMessage,
} from "ai";

import { createSupabaseServerClient } from "@/lib/supabase/server";

import { BACKEND_BASE_URL } from "@/lib/api/backend-url";
import { UUID_RE } from "@/lib/validation";

import { STATIC_SYSTEM_PROMPT } from "./system-prompt";
import { productAssistantTools } from "./tools";

export const maxDuration = 60;

// --- Header bundle in-process cache ---

interface HeaderCacheEntry {
  at: number;
  data: unknown;
}

const HEADER_CACHE_TTL_MS = 10_000;
const HEADER_CACHE_MAX_SIZE = 200;
const headerCache = new Map<string, HeaderCacheEntry>();

setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of headerCache) {
    if (now - entry.at > HEADER_CACHE_TTL_MS) headerCache.delete(key);
  }
}, 60_000).unref();

/** Exported for tests. */
export function clearHeaderCache(productId?: string): void {
  if (productId) headerCache.delete(productId);
  else headerCache.clear();
}

async function getHeaderBundle(
  productId: string,
  jwt: string,
): Promise<unknown> {
  const now = Date.now();
  const cached = headerCache.get(productId);
  if (cached && now - cached.at < HEADER_CACHE_TTL_MS) {
    return cached.data;
  }
  const res = await fetch(
    `${BACKEND_BASE_URL}/api/analytics/products/${productId}/report-bundle/header`,
    { headers: { Authorization: `Bearer ${jwt}` } },
  );
  if (!res.ok) {
    throw new Error(`header bundle fetch failed: ${res.status}`);
  }
  const data = await res.json();
  headerCache.set(productId, { at: now, data });
  if (headerCache.size > HEADER_CACHE_MAX_SIZE) headerCache.clear();
  return data;
}

// --- Rate limit (per user, 20 msgs / 10 min) ---

interface RateLimitBucket {
  windowStart: number;
  count: number;
}

const RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
const RATE_LIMIT_MAX = 20;
const rateBuckets = new Map<string, RateLimitBucket>();

setInterval(() => {
  const now = Date.now();
  for (const [key, bucket] of rateBuckets) {
    if (now - bucket.windowStart > RATE_LIMIT_WINDOW_MS) rateBuckets.delete(key);
  }
}, RATE_LIMIT_WINDOW_MS).unref();

function checkRateLimit(userId: string): boolean {
  const now = Date.now();
  const bucket = rateBuckets.get(userId);
  if (!bucket || now - bucket.windowStart > RATE_LIMIT_WINDOW_MS) {
    rateBuckets.set(userId, { windowStart: now, count: 1 });
    return true;
  }
  if (bucket.count >= RATE_LIMIT_MAX) return false;
  bucket.count += 1;
  return true;
}

// --- Helpers ---

function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function isSameOriginRequest(req: Request): boolean {
  const origin = req.headers.get("origin");
  if (!origin) {
    // Browsers omit Origin on same-origin GET but always set it on POST.
    // Reject to keep the CSRF boundary explicit.
    return false;
  }
  const host = req.headers.get("host");
  if (!host) return false;
  try {
    const parsed = new URL(origin);
    return parsed.host === host;
  } catch {
    return false;
  }
}

// --- Handler ---

export async function POST(req: Request): Promise<Response> {
  // 1. CSRF origin check
  if (!isSameOriginRequest(req)) {
    return jsonError(403, "invalid_origin", "Origin mismatch");
  }

  // 2. Auth + admin role
  const supabase = await createSupabaseServerClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session) {
    return jsonError(401, "unauthenticated", "Sign in required");
  }
  const role = (
    (session.user.user_metadata?.role as string | undefined) ?? ""
  ).toUpperCase();
  if (role !== "ADMIN") {
    return jsonError(403, "forbidden", "Admin role required");
  }

  // 3. Rate limit
  if (!checkRateLimit(session.user.id)) {
    return jsonError(429, "rate_limited", "Too many messages; try again later");
  }

  // 4. Parse + validate body. The productId comes from the client-supplied
  //    body only as a hint; what actually binds tool calls is the
  //    experimental_context closure. Any productId the LLM tries to put in a
  //    tool argument is structurally impossible because the schema does not
  //    have that field.
  let body: { messages?: UIMessage[]; productId?: unknown };
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return jsonError(400, "invalid_body", "Malformed JSON");
  }
  const productId = body.productId;
  if (typeof productId !== "string" || !UUID_RE.test(productId)) {
    return jsonError(400, "invalid_product_id", "productId must be a UUID");
  }
  const messages = Array.isArray(body.messages) ? body.messages : [];

  // 5. Cap conversation history to last 20 turns
  const recent = messages.slice(-20);

  // 6. Fetch header bundle (cached) and inject as dynamic system message
  let header: unknown;
  try {
    header = await getHeaderBundle(productId, session.access_token);
  } catch {
    return jsonError(
      502,
      "backend_unavailable",
      "Could not load product header",
    );
  }

  const modelId = process.env.INVENTORY_ASSISTANT_MODEL ?? "gemini-2.5-flash";
  const contextSystemMsg = `Current product header bundle (read-only, do not cite verbatim):\n${JSON.stringify(header)}`;

  const result = streamText({
    model: google(modelId),
    system: `${STATIC_SYSTEM_PROMPT}\n\n${contextSystemMsg}`,
    messages: await convertToModelMessages(recent),
    tools: productAssistantTools,
    stopWhen: stepCountIs(5),
    experimental_context: { productId, jwt: session.access_token },
    abortSignal: req.signal,
  });

  return result.toUIMessageStreamResponse();
}
