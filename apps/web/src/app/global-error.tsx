"use client";

import React, { useEffect } from "react";
import { reportError } from "@/lib/error-reporting";

interface GlobalErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function GlobalError({ error, reset }: GlobalErrorProps) {
  useEffect(() => {
    reportError("Global error", error);
  }, [error]);

  const handleReload = () => {
    window.location.reload();
  };

  return (
    <html lang="en">
      <body className="font-sans antialiased">
        <div className="flex min-h-screen flex-col items-center justify-center bg-gray-50 px-4 dark:bg-gray-900">
          <div className="mx-auto max-w-md text-center">
            <div className="mb-4 text-7xl text-red-600">!</div>
            <h1 className="mb-2 text-3xl font-bold text-gray-900 dark:text-gray-100">
              Application Error
            </h1>
            <p className="mb-6 text-gray-600 dark:text-gray-400">
              {error.message || "A critical error occurred. Please reload the page."}
            </p>
            <div className="flex flex-col gap-3 sm:flex-row sm:justify-center">
              <button
                onClick={reset}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
              >
                Try again
              </button>
              <button
                onClick={handleReload}
                className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
              >
                Reload page
              </button>
            </div>
          </div>
        </div>
      </body>
    </html>
  );
}
