import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Natural sort comparator for strings containing numbers.
 * Sorts "B1", "B2", "B10" correctly instead of "B1", "B10", "B2".
 */
export function naturalSortCompare(a: string, b: string): number {
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' })
}

/** Display form of prize letter: "Last Prize" (or truncated "La") → "LP", otherwise the letter as-is. */
export function prizeLetterDisplay(letter: string | null | undefined): string {
  if (letter == null || letter === "") return "";
  const normalized = letter.trim().toLowerCase();
  if (normalized === "last prize" || normalized === "la") return "LP";
  return letter;
}
