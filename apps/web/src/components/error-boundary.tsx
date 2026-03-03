"use client";

import { Component, type ReactNode } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
  onReset?: () => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * Error boundary component that catches render errors in child components.
 * Displays a fallback UI and provides a retry mechanism.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null });
    this.props.onReset?.();
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <Alert variant="destructive" className="my-4">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Something went wrong</AlertTitle>
          <AlertDescription className="mt-2">
            <p className="text-sm mb-3">
              An error occurred while rendering this section.
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={this.handleRetry}
              className="gap-1.5"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              Try Again
            </Button>
          </AlertDescription>
        </Alert>
      );
    }

    return this.props.children;
  }
}

interface DashboardErrorFallbackProps {
  onRetry: () => void;
}

/**
 * Dashboard-specific error fallback with full-page styling.
 */
export function DashboardErrorFallback({ onRetry }: DashboardErrorFallbackProps) {
  return (
    <div className="flex flex-col items-center justify-center min-h-[400px] p-8">
      <div className="text-center max-w-md">
        <div className="mx-auto w-12 h-12 rounded-full bg-destructive/10 flex items-center justify-center mb-4">
          <AlertTriangle className="h-6 w-6 text-destructive" />
        </div>
        <h2 className="text-lg font-semibold mb-2">Dashboard Error</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Something went wrong while loading the dashboard. This might be a temporary issue.
        </p>
        <Button onClick={onRetry} className="gap-1.5">
          <RefreshCw className="h-4 w-4" />
          Reload Dashboard
        </Button>
      </div>
    </div>
  );
}
