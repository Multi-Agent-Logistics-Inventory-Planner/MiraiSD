"use client";

import React, { useEffect } from "react";
import { reportError } from "@/lib/error-reporting";

interface ErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function ErrorBoundary({ error, reset }: ErrorProps) {
  useEffect(() => {
    reportError("Route error", error);
  }, [error]);

  return (
    <div className="flex min-h-[400px] flex-col items-center justify-center px-4">
      <div className="mx-auto max-w-md text-center">
        <div className="mb-4 text-6xl text-red-500">!</div>
        <h2 className="mb-2 text-2xl font-semibold text-gray-900 dark:text-gray-100">
          Something went wrong
        </h2>
        <p className="mb-6 text-gray-600 dark:text-gray-400">
          {error.message || "An unexpected error occurred. Please try again."}
        </p>
        <button
          onClick={reset}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          Try again
        </button>
      </div>
    </div>
  );
}
