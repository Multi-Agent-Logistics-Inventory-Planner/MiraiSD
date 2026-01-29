import { z } from "zod";

/**
 * Validates that a URL uses a safe protocol (http or https)
 * Returns the URL if valid, undefined otherwise
 */
export function getSafeImageUrl(url: string | undefined | null): string | undefined {
  if (!url) return undefined;

  try {
    const parsed = new URL(url);
    if (parsed.protocol === "https:" || parsed.protocol === "http:") {
      return url;
    }
    return undefined;
  } catch {
    // If URL parsing fails, check if it's a relative path (starts with /)
    if (url.startsWith("/")) {
      return url;
    }
    return undefined;
  }
}

/**
 * Zod schema for validating stock quantity input
 */
export const quantitySchema = z
  .number()
  .int("Quantity must be a whole number")
  .min(0, "Quantity cannot be negative");

/**
 * Zod schema for validating quantity change (can be positive or negative)
 */
export const quantityChangeSchema = z
  .number()
  .int("Quantity change must be a whole number");

/**
 * Parse and validate a quantity string input
 * Returns the parsed number or null if invalid
 */
export function parseQuantityInput(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === "" || !/^\d+$/.test(trimmed)) {
    return null;
  }

  const parsed = parseInt(trimmed, 10);
  const result = quantitySchema.safeParse(parsed);

  return result.success ? result.data : null;
}

/**
 * Clamp a quantity value within bounds
 */
export function clampQuantity(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(value, max));
}
