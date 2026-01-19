"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getSupabaseClient, getSupabaseEnvStatus } from "@/lib/supabase";

type FetchResult<T> =
  | { ok: true; data: T; status: number }
  | { ok: false; error: string; status?: number };

const API_BASE_URL =
  process.env.NEXT_PUBLIC_DASHBOARD_API_URL ?? "http://localhost:5001";

async function fetchJson<T>(path: string, accessToken?: string): Promise<FetchResult<T>> {
  try {
    const res = await fetch(`${API_BASE_URL}${path}`, {
      cache: "no-store",
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      return {
        ok: false,
        status: res.status,
        error: text || `Request failed: ${res.status} ${res.statusText}`,
      };
    }

    const data = (await res.json()) as T;
    return { ok: true, data, status: res.status };
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return { ok: false, error: msg };
  }
}

function formatCell(value: unknown): string {
  if (value === null) return "null";
  if (value === undefined) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return JSON.stringify(value);
}

function KeyValueTable({ value }: { value: unknown }) {
  const obj = value as Record<string, unknown> | null;
  if (!obj || typeof obj !== "object" || Array.isArray(obj)) {
    return <pre>{JSON.stringify(value, null, 2)}</pre>;
  }

  const entries = Object.entries(obj);
  return (
    <table border={1} cellPadding={6}>
      <thead>
        <tr>
          <th>key</th>
          <th>value</th>
        </tr>
      </thead>
      <tbody>
        {entries.map(([k, v]) => (
          <tr key={k}>
            <td>
              <code>{k}</code>
            </td>
            <td>
              <code>{formatCell(v)}</code>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ArrayTable({ rows }: { rows: Array<Record<string, unknown>> }) {
  const columns = Array.from(
    rows.reduce((set, r) => {
      for (const k of Object.keys(r)) set.add(k);
      return set;
    }, new Set<string>())
  );

  if (rows.length === 0) return <p>(empty)</p>;

  return (
    <table border={1} cellPadding={6}>
      <thead>
        <tr>
          {columns.map((c) => (
            <th key={c}>
              <code>{c}</code>
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((r, idx) => (
          <tr key={idx}>
            {columns.map((c) => (
              <td key={c}>
                <code>{formatCell(r[c])}</code>
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ResultBlock({
  title,
  result,
  renderOk,
}: {
  title: string;
  result: FetchResult<unknown> | null;
  renderOk?: (data: unknown) => React.ReactNode;
}) {
  return (
    <section>
      <h2>{title}</h2>
      {!result ? (
        <p>(loading)</p>
      ) : result.ok ? (
        <>
          {renderOk ? renderOk(result.data) : <KeyValueTable value={result.data} />}
          <details>
            <summary>raw json</summary>
            <pre>{JSON.stringify(result, null, 2)}</pre>
          </details>
        </>
      ) : (
        <>
          <p>
            Error{result.status ? ` (${result.status})` : ""}: <code>{result.error}</code>
          </p>
          <details>
            <summary>raw json</summary>
            <pre>{JSON.stringify(result, null, 2)}</pre>
          </details>
        </>
      )}
    </section>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const supabase = getSupabaseClient();
  const envStatus = getSupabaseEnvStatus();
  const [email, setEmail] = useState<string | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [health, setHealth] = useState<FetchResult<unknown> | null>(null);
  const [summary, setSummary] = useState<FetchResult<unknown> | null>(null);
  const [items, setItems] = useState<FetchResult<unknown> | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      const sessionRes = supabase ? await supabase.auth.getSession() : { data: { session: null } };
      const token = sessionRes.data.session?.access_token ?? null;
      const userEmail = sessionRes.data.session?.user?.email ?? null;
      if (cancelled) return;

      setAccessToken(token);
      setEmail(userEmail);

      const [h, s, i] = await Promise.all([
        fetchJson("/health", token ?? undefined),
        fetchJson("/inventory/summary", token ?? undefined),
        fetchJson("/items?limit=10&includeForecasts=true", token ?? undefined),
      ]);

      if (cancelled) return;
      setHealth(h);
      setSummary(s);
      setItems(i);
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  async function logout() {
    if (supabase) {
      await supabase.auth.signOut();
    }
    router.push("/login");
    router.refresh();
  }

  return (
    <main>
      <h1>Dashboard</h1>
      <p>API base: {API_BASE_URL}</p>
      {!envStatus.hasUrl || !envStatus.hasAnonKey ? (
        <p>
          Supabase env missing (login won’t work). Create{" "}
          <code>apps/web/.env.local</code> with{" "}
          <code>NEXT_PUBLIC_SUPABASE_URL</code> and{" "}
          <code>NEXT_PUBLIC_SUPABASE_ANON_KEY</code>, then restart{" "}
          <code>npm run dev</code>.
        </p>
      ) : null}
      <p>
        Signed in as: {email ?? "(not signed in)"}{" "}
        {email ? (
          <button type="button" onClick={logout}>
            Log out
          </button>
        ) : (
          <a href="/login">Log in</a>
        )}
      </p>
      <p>Token present: {accessToken ? "yes" : "no"}</p>

      <ResultBlock title="/health" result={health} />
      <ResultBlock
        title="/inventory/summary"
        result={summary}
        renderOk={(data) => {
          const d = data as any;
          const byLocation = d?.byLocation && typeof d.byLocation === "object" ? d.byLocation : null;
          return (
            <>
              <KeyValueTable value={{ ...d, byLocation: undefined }} />
              <h3>byLocation</h3>
              {byLocation ? (
                <ArrayTable
                  rows={Object.entries(byLocation).map(([location, v]) => ({
                    location,
                    ...(v as Record<string, unknown>),
                  }))}
                />
              ) : (
                <p>(no byLocation)</p>
              )}
            </>
          );
        }}
      />
      <ResultBlock
        title="/items?limit=10&includeForecasts=true"
        result={items}
        renderOk={(data) => {
          const d = data as any;
          const rows = Array.isArray(d?.data) ? (d.data as Array<Record<string, unknown>>) : [];
          return (
            <>
              <ArrayTable rows={rows} />
              <h3>pagination</h3>
              <KeyValueTable value={d?.pagination ?? null} />
            </>
          );
        }}
      />
    </main>
  );
}

