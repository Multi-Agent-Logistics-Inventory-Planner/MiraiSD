import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PredictionItemCard } from "../prediction-item-card";
import type { ActionItem } from "@/types/analytics";

function createMockActionItem(overrides?: Partial<ActionItem>): ActionItem {
  return {
    itemId: "item-1",
    name: "Test Product",
    sku: "SKU-001",
    imageUrl: "https://example.com/image.jpg",
    categoryName: "Snacks",
    currentStock: 15,
    reorderPoint: 10,
    targetStockLevel: 50,
    daysToStockout: 5.5,
    avgDailyDelta: -2.5,
    suggestedReorderQty: 40,
    suggestedOrderDate: "2026-04-05",
    leadTimeDays: 3,
    demandVelocity: 2.5,
    demandVolatility: 0.3,
    forecastAccuracy: 0.85,
    confidence: 0.9,
    urgency: "URGENT",
    overdue: false,
    computedAt: new Date().toISOString(),
    ...overrides,
  };
}

describe("PredictionItemCard", () => {
  describe("lazy loading images", () => {
    it("renders img elements with loading=lazy attribute", () => {
      const item = createMockActionItem({
        imageUrl: "https://example.com/product.jpg",
      });

      const { container } = render(
        <PredictionItemCard item={item} showUrgencyColor={true} />,
      );

      const images = container.querySelectorAll("img");
      expect(images.length).toBeGreaterThan(0);

      for (const img of images) {
        expect(img).toHaveAttribute("loading", "lazy");
      }
    });

    it("sets correct alt text on images", () => {
      const item = createMockActionItem({
        name: "Pocky Sticks",
        imageUrl: "https://example.com/pocky.jpg",
      });

      render(
        <PredictionItemCard item={item} showUrgencyColor={false} />,
      );

      const images = screen.getAllByRole("img");
      for (const img of images) {
        expect(img).toHaveAttribute("alt", "Pocky Sticks");
      }
    });

    it("renders fallback icon when imageUrl is null", () => {
      const item = createMockActionItem({ imageUrl: null });

      const { container } = render(
        <PredictionItemCard item={item} showUrgencyColor={true} />,
      );

      const images = container.querySelectorAll("img");
      expect(images.length).toBe(0);
    });
  });

  describe("urgency color", () => {
    it("applies urgency color when showUrgencyColor is true", () => {
      const item = createMockActionItem({ daysToStockout: 2 });

      const { container } = render(
        <PredictionItemCard item={item} showUrgencyColor={true} />,
      );

      // daysToStockout=2 is <= CRITICAL threshold (3), so red color class
      const redElements = container.querySelectorAll(".text-red-600");
      expect(redElements.length).toBeGreaterThan(0);
    });

    it("uses default foreground color when showUrgencyColor is false", () => {
      const item = createMockActionItem({ daysToStockout: 2 });

      const { container } = render(
        <PredictionItemCard item={item} showUrgencyColor={false} />,
      );

      const redElements = container.querySelectorAll(".text-red-600");
      expect(redElements.length).toBe(0);
    });
  });

  describe("overdue badge", () => {
    it("shows overdue badge when item is overdue", () => {
      const item = createMockActionItem({ overdue: true });

      render(
        <PredictionItemCard item={item} showUrgencyColor={true} />,
      );

      const badges = screen.getAllByText("Overdue");
      expect(badges.length).toBeGreaterThan(0);
    });

    it("does not show overdue badge when item is not overdue", () => {
      const item = createMockActionItem({ overdue: false });

      render(
        <PredictionItemCard item={item} showUrgencyColor={true} />,
      );

      expect(screen.queryByText("Overdue")).not.toBeInTheDocument();
    });
  });

  describe("dismiss and restore buttons", () => {
    it("renders dismiss button when onDismiss is provided", () => {
      const item = createMockActionItem();

      render(
        <PredictionItemCard
          item={item}
          showUrgencyColor={true}
          onDismiss={() => {}}
        />,
      );

      expect(screen.getByLabelText("Dismiss prediction")).toBeInTheDocument();
    });

    it("renders restore button when onRestore is provided", () => {
      const item = createMockActionItem();

      render(
        <PredictionItemCard
          item={item}
          showUrgencyColor={true}
          onRestore={() => {}}
        />,
      );

      expect(screen.getByLabelText("Restore prediction")).toBeInTheDocument();
    });
  });
});
