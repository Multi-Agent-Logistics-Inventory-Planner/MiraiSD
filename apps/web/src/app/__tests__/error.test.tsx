import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ErrorBoundary from "../error";
import * as errorReporting from "@/lib/error-reporting";

vi.mock("@/lib/error-reporting", () => ({
  reportError: vi.fn(),
}));

describe("ErrorBoundary component", () => {
  const mockReset = vi.fn();
  const testError = new Error("Test error message");

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should render error heading", () => {
    render(<ErrorBoundary error={testError} reset={mockReset} />);
    expect(screen.getByRole("heading")).toHaveTextContent("Something went wrong");
  });

  it("should display error message", () => {
    render(<ErrorBoundary error={testError} reset={mockReset} />);
    expect(screen.getByText(/Test error message/)).toBeInTheDocument();
  });

  it("should render try again button", () => {
    render(<ErrorBoundary error={testError} reset={mockReset} />);
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("should call reset when try again button is clicked", () => {
    render(<ErrorBoundary error={testError} reset={mockReset} />);

    const button = screen.getByRole("button", { name: /try again/i });
    fireEvent.click(button);

    expect(mockReset).toHaveBeenCalledTimes(1);
  });

  it("should handle error with digest property", () => {
    const errorWithDigest = Object.assign(new Error("Digest error"), {
      digest: "abc123",
    });

    render(<ErrorBoundary error={errorWithDigest} reset={mockReset} />);
    expect(screen.getByText(/Digest error/)).toBeInTheDocument();
  });

  it("should handle error without message gracefully", () => {
    const errorWithoutMessage = new Error();

    render(<ErrorBoundary error={errorWithoutMessage} reset={mockReset} />);
    expect(screen.getByRole("heading")).toHaveTextContent("Something went wrong");
  });

  it("should call reportError with correct context and error", () => {
    render(<ErrorBoundary error={testError} reset={mockReset} />);

    expect(errorReporting.reportError).toHaveBeenCalledTimes(1);
    expect(errorReporting.reportError).toHaveBeenCalledWith(
      "Route error",
      testError
    );
  });
});
