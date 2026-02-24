import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import GlobalError from "../global-error";
import * as errorReporting from "@/lib/error-reporting";

vi.mock("@/lib/error-reporting", () => ({
  reportError: vi.fn(),
}));

describe("GlobalError component", () => {
  const mockReset = vi.fn();
  const testError = new Error("Global error message");

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should render error heading", () => {
    render(<GlobalError error={testError} reset={mockReset} />);
    expect(screen.getByRole("heading")).toHaveTextContent("Application Error");
  });

  it("should display error message", () => {
    render(<GlobalError error={testError} reset={mockReset} />);
    expect(screen.getByText(/Global error message/)).toBeInTheDocument();
  });

  it("should render try again button", () => {
    render(<GlobalError error={testError} reset={mockReset} />);
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("should call reset when try again button is clicked", () => {
    render(<GlobalError error={testError} reset={mockReset} />);

    const button = screen.getByRole("button", { name: /try again/i });
    fireEvent.click(button);

    expect(mockReset).toHaveBeenCalledTimes(1);
  });

  it("should render reload button", () => {
    render(<GlobalError error={testError} reset={mockReset} />);
    expect(screen.getByRole("button", { name: /reload page/i })).toBeInTheDocument();
  });

  it("should handle error with digest property", () => {
    const errorWithDigest = Object.assign(new Error("Digest global error"), {
      digest: "xyz789",
    });

    render(<GlobalError error={errorWithDigest} reset={mockReset} />);
    expect(screen.getByText(/Digest global error/)).toBeInTheDocument();
  });

  it("should call reportError with correct context and error", () => {
    render(<GlobalError error={testError} reset={mockReset} />);

    expect(errorReporting.reportError).toHaveBeenCalledTimes(1);
    expect(errorReporting.reportError).toHaveBeenCalledWith(
      "Global error",
      testError
    );
  });
});
