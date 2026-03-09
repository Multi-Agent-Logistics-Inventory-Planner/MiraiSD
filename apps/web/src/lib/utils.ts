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

/**
 * Sort prizes in order: LP first, then A, B, C, etc.
 * Items without a letter are sorted to the end.
 */
export function sortPrizes<T extends { letter?: string | null }>(prizes: T[]): T[] {
  return [...prizes].sort((a, b) => {
    const letterA = prizeLetterDisplay(a.letter);
    const letterB = prizeLetterDisplay(b.letter);

    // Empty letters go to end
    if (!letterA && !letterB) return 0;
    if (!letterA) return 1;
    if (!letterB) return -1;

    // LP (Last Prize) always comes first
    if (letterA === "LP" && letterB !== "LP") return -1;
    if (letterB === "LP" && letterA !== "LP") return 1;
    if (letterA === "LP" && letterB === "LP") return 0;

    // Alphabetical order for other letters
    return letterA.localeCompare(letterB, undefined, { numeric: true, sensitivity: "base" });
  });
}
