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
