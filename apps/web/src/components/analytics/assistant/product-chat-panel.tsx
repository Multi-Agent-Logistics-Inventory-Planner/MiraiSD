"use client";

import { useChat } from "@ai-sdk/react";
import { DefaultChatTransport } from "ai";
import { ArrowLeft, Send, Square } from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";

import { useProductReportBundle } from "@/hooks/queries/use-product-report-bundle";

const EXAMPLE_PROMPTS = [
  "Is demand trending up or down?",
  "How many days until stockout at current velocity?",
  "When was the last restock?",
  "How does this compare to other items in its category?",
];

interface ProductChatPanelProps {
  productId: string;
}

/**
 * Chat side of the Product Assistant. Uses AI SDK v6 patterns: the api and
 * body options live on DefaultChatTransport, and sendMessage({ text })
 * replaces v5's append.
 */
export function ProductChatPanel({ productId }: ProductChatPanelProps) {
  const router = useRouter();
  const { data } = useProductReportBundle(productId);

  const transport = useMemo(
    () =>
      new DefaultChatTransport({
        api: "/api/chat/product-assistant",
        body: { productId },
      }),
    [productId],
  );

  const { messages, sendMessage, status, stop, error } = useChat({
    id: `product-${productId}`,
    transport,
  });

  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const busy = status === "submitted" || status === "streaming";

  const submit = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || busy) return;
    sendMessage({ text: trimmed });
    setInput("");
  };

  return (
    <div className="flex h-[70vh] flex-col rounded-lg border bg-card">
      <div className="flex items-center gap-3 border-b p-2">
        {data?.product.imageUrl ? (
          <Image
            src={data.product.imageUrl}
            alt={data.product.name}
            width={36}
            height={36}
            sizes="36px"
            className="h-9 w-9 rounded object-cover"
          />
        ) : (
          <div className="h-12 w-12 rounded bg-muted" />
        )}
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="truncate text-sm font-semibold">
            {data?.product.name ?? "Loading..."}
          </div>
          <div className="truncate text-xs text-muted-foreground">
            {data?.product.categoryName ?? ""}
          </div>
        </div>
        <button
          type="button"
          onClick={() => router.push("/analytics?tab=assistant")}
          className="inline-flex shrink-0 items-center gap-1 rounded-md border bg-background px-2.5 py-1.5 text-xs hover:bg-muted"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Change
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4">
        {messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-3 text-sm text-muted-foreground">
            <p>Ask anything about this product. For example:</p>
            <div className="flex flex-wrap justify-center gap-2">
              {EXAMPLE_PROMPTS.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => submit(p)}
                  className="rounded-full border bg-background px-3 py-1.5 text-xs hover:bg-muted"
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <ul className="space-y-3">
            {messages.map((m) => (
              <li
                key={m.id}
                className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[80%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm ${
                    m.role === "user"
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted"
                  }`}
                >
                  {m.parts.map((part, i) =>
                    part.type === "text" ? (
                      <span key={i}>{part.text}</span>
                    ) : null,
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        {error && (
          <div className="mt-3 rounded-md border border-destructive/40 bg-destructive/5 p-2 text-xs text-destructive">
            Something went wrong. Please try again.
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit(input);
        }}
        className="flex gap-2 border-t p-3"
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about this product…"
          disabled={busy}
          aria-label="Chat input"
          className="flex-1 rounded-md border bg-background px-3 py-2 text-sm outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
        />
        {busy ? (
          <button
            type="button"
            onClick={() => stop()}
            aria-label="Stop"
            className="inline-flex items-center gap-1 rounded-md border bg-background px-3 py-2 text-sm hover:bg-muted"
          >
            <Square className="h-4 w-4" />
            Stop
          </button>
        ) : (
          <button
            type="submit"
            aria-label="Send"
            disabled={!input.trim()}
            className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground disabled:opacity-50"
          >
            <Send className="h-4 w-4" />
            Send
          </button>
        )}
      </form>
    </div>
  );
}
