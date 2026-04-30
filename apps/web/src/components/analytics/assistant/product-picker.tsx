"use client";

import { Search, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useRef, useState } from "react";

import { ProductThumbnail } from "@/components/products/product-thumbnail";
import { useProducts } from "@/hooks/queries/use-products";

/**
 * Command-palette style product picker shown when the Assistant tab has no
 * productId in the URL. Filters client-side over the already-loaded catalog.
 */
export function ProductPicker() {
  const router = useRouter();
  const { data: products, isLoading } = useProducts({ rootOnly: true });
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const filtered = useMemo(() => {
    const base = (products ?? []).filter((p) => p.isActive);
    const q = query.trim().toLowerCase();
    if (!q) return base.slice(0, 20);
    return base.filter((p) => p.name.toLowerCase().includes(q)).slice(0, 20);
  }, [products, query]);

  const select = (productId: string) => {
    router.push(`/analytics?tab=assistant&productId=${productId}`);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, filtered.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const picked = filtered[activeIndex];
      if (picked) select(picked.id);
    }
  };

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-4 py-8 -my-3">
      <h2 className="text-center text-secondary-foreground text-xl font-medium">
        Select product to analyze
      </h2>

      <div className="overflow-hidden rounded-lg border bg-card">
        <div className="relative border-b py-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setActiveIndex(0);
            }}
            onKeyDown={onKeyDown}
            placeholder="Search products"
            aria-label="Search products"
            className="w-full bg-transparent py-2.5 pl-10 pr-10 text-sm outline-none"
          />
          {query && (
            <button
              type="button"
              onClick={() => {
                setQuery("");
                inputRef.current?.focus();
              }}
              aria-label="Clear search"
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        <div className="max-h-[60vh] overflow-y-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {isLoading ? (
            <div className="p-4 text-sm text-muted-foreground">Loading…</div>
          ) : filtered.length === 0 ? (
            <div className="p-4 text-sm text-muted-foreground">
              No products match.
            </div>
          ) : (
            <ul role="listbox" aria-label="Products">
              {filtered.map((p, i) => {
                const isActive = i === activeIndex;
                return (
                  <li key={p.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={isActive}
                      onMouseEnter={() => setActiveIndex(i)}
                      onClick={() => select(p.id)}
                      className={`flex w-full items-center gap-3 px-4 py-2 text-left text-sm transition-colors cursor-pointer ${
                        isActive ? "bg-muted" : "hover:bg-muted/60"
                      }`}
                    >
                      <ProductThumbnail
                        imageUrl={p.imageUrl}
                        alt={p.name}
                        size="sm"
                        fallbackText=""
                      />
                      <span className="flex-1 truncate">{p.name}</span>
                      <span className="truncate text-xs text-muted-foreground">
                        {p.category?.name}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
