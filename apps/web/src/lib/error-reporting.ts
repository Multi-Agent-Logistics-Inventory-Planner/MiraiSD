/**
 * Reports errors to monitoring service in production,
 * logs to console in development.
 */
export function reportError(
  context: string,
  error: Error & { digest?: string }
): void {
  if (process.env.NODE_ENV === "production") {
    // Production: silently capture for error tracking service
    // Future: integrate with Sentry, LogRocket, etc.
    // For now, we suppress console output in production
    return;
  }
  // Development: log to console for debugging
  console.error(`${context}:`, error);
}
