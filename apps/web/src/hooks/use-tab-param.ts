"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

interface UseTabParamOptions<T extends string> {
  values: readonly T[];
  defaultValue: T | null;
  paramName?: string;
}

interface UseTabParamResult<T extends string> {
  value: T | null;
  setValue: (next: T) => void;
  mountedValues: Set<T>;
}

export function useTabParam<T extends string>(
  options: UseTabParamOptions<T>
): UseTabParamResult<T> {
  const { values, defaultValue, paramName = "tab" } = options;
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const raw = searchParams.get(paramName);
  const value = useMemo<T | null>(() => {
    if (raw !== null && (values as readonly string[]).includes(raw)) {
      return raw as T;
    }
    return defaultValue;
  }, [raw, values, defaultValue]);

  const setValue = useCallback(
    (next: T) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set(paramName, next);
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [router, pathname, searchParams, paramName]
  );

  const [mountedValues, setMountedValues] = useState<Set<T>>(() =>
    value === null ? new Set<T>() : new Set<T>([value])
  );

  useEffect(() => {
    if (value === null) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- accumulating "has ever been visited" set; cascade is once per new tab
    setMountedValues((prev) => {
      if (prev.has(value)) return prev;
      const next = new Set(prev);
      next.add(value);
      return next;
    });
  }, [value]);

  return { value, setValue, mountedValues };
}
