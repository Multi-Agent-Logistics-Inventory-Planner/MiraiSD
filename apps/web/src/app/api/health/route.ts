import { NextResponse } from "next/server";

// Liveness probe for the container healthcheck in infra/docker-compose.yml.
// Must never be statically rendered or cached, or it would stop reflecting
// whether this process is actually serving.
export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json({ status: "ok" });
}
