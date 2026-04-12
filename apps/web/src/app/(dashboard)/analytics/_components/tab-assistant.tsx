"use client";

import { useSearchParams } from "next/navigation";

import {
  ProductChatPanel,
  ProductPicker,
  ProductReportPanel,
} from "@/components/analytics/assistant";
import { UUID_RE } from "@/lib/validation";

export function TabAssistant() {
  const searchParams = useSearchParams();
  const productId = searchParams.get("productId");
  const hasValidProduct = productId != null && UUID_RE.test(productId);

  if (!hasValidProduct) {
    return <ProductPicker />;
  }

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <ProductReportPanel productId={productId} />
      <ProductChatPanel productId={productId} />
    </div>
  );
}
