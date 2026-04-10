"use client";

import { ArrowLeft } from "lucide-react";
import { useRouter } from "next/navigation";

export function AnalyzeAnotherButton() {
  const router = useRouter();
  return (
    <button
      type="button"
      onClick={() => router.push("/analytics?tab=assistant")}
      className="inline-flex items-center gap-1 rounded-md border bg-background px-3 py-1.5 text-xs hover:bg-muted"
    >
      <ArrowLeft className="h-3.5 w-3.5" />
      Analyze different product
    </button>
  );
}
