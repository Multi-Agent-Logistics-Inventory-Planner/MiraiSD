import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ActionItem, PredictionsData } from "@/types/analytics";

// Mock hooks before importing the component
const mockUsePredictions = vi.fn();
const mockUseDismissedPredictions = vi.fn();

vi.mock("@/hooks/queries/use-predictions", () => ({
  usePredictions: () => mockUsePredictions(),
}));

vi.mock("@/hooks/use-dismissed-predictions", () => ({
  useDismissedPredictions: () => mockUseDismissedPredictions(),
}));

// Mock child components to isolate TabPredictions logic
vi.mock("@/components/analytics/predictions", async () => {
  const actual = await vi.importActual("@/components/analytics/predictions");
  return {
    ...actual,
    PredictionItemCard: ({ item }: { item: ActionItem }) => (
      <div data-testid={`prediction-card-${item.itemId}`}>{item.name}</div>
    ),
    UrgencyTabs: ({ tabs, activeTab, onTabChange }: {
      tabs: Array<{ value: string; label: string; count: number }>;
      activeTab: string;
      onTabChange: (tab: string) => void;
    }) => (
      <div data-testid="urgency-tabs">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            data-testid={`tab-${tab.value}`}
            data-active={tab.value === activeTab}
            onClick={() => onTabChange(tab.value)}
          >
            {tab.label} ({tab.count})
          </button>
        ))}
      </div>
    ),
    MobileFilterControls: () => <div data-testid="mobile-filters" />,
    DesktopFilterControls: () => <div data-testid="desktop-filters" />,
    PredictionsPagination: () => <div data-testid="pagination" />,
    PredictionsSkeleton: () => <div data-testid="predictions-skeleton" />,
  };
});

import { TabPredictions } from "../_components/tab-predictions";

function createMockItem(overrides: Partial<ActionItem>): ActionItem {
  return {
    itemId: "item-1",
    name: "Test Product",
    sku: "SKU-001",
    imageUrl: null,
    categoryName: "Snacks",
    currentStock: 15,
    reorderPoint: 10,
    targetStockLevel: 50,
    daysToStockout: 5,
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

function createMockPredictionsData(items: ActionItem[]): PredictionsData {
  return {
    items,
    totalActionItems: items.length,
    avgForecastAccuracy: 0.85,
    totalDemandVelocity: 10,
    riskSummary: { critical: 0, urgent: 0, attention: 0, healthy: 0 },
  };
}

describe("TabPredictions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseDismissedPredictions.mockReturnValue({
      dismissedMap: {},
      dismissedIds: new Set(),
      dismiss: vi.fn(),
      restore: vi.fn(),
    });
  });

  describe("loading state", () => {
    it("renders skeleton while loading", () => {
      mockUsePredictions.mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
      });

      render(<TabPredictions />);

      expect(screen.getByTestId("predictions-skeleton")).toBeInTheDocument();
    });
  });

  describe("error state", () => {
    it("renders error message on failure", () => {
      mockUsePredictions.mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: true,
      });

      render(<TabPredictions />);

      expect(screen.getByText("Failed to load predictions")).toBeInTheDocument();
    });
  });

  describe("urgency tab counts (countByUrgency)", () => {
    it("displays correct counts for each urgency level", () => {
      const items = [
        createMockItem({ itemId: "c1", urgency: "CRITICAL", daysToStockout: 1 }),
        createMockItem({ itemId: "c2", urgency: "CRITICAL", daysToStockout: 2 }),
        createMockItem({ itemId: "u1", urgency: "URGENT", daysToStockout: 5 }),
        createMockItem({ itemId: "a1", urgency: "ATTENTION", daysToStockout: 10 }),
        createMockItem({ itemId: "a2", urgency: "ATTENTION", daysToStockout: 12 }),
        createMockItem({ itemId: "a3", urgency: "ATTENTION", daysToStockout: 13 }),
        createMockItem({ itemId: "h1", urgency: "HEALTHY", daysToStockout: 20 }),
      ];

      mockUsePredictions.mockReturnValue({
        data: createMockPredictionsData(items),
        isLoading: false,
        isError: false,
      });

      render(<TabPredictions />);

      // All tab shows total count
      expect(screen.getByTestId("tab-ALL")).toHaveTextContent("All (7)");
      expect(screen.getByTestId("tab-CRITICAL")).toHaveTextContent("Critical (2)");
      expect(screen.getByTestId("tab-URGENT")).toHaveTextContent("Urgent (1)");
      expect(screen.getByTestId("tab-ATTENTION")).toHaveTextContent("Attention (3)");
      expect(screen.getByTestId("tab-HEALTHY")).toHaveTextContent("Safe (1)");
      expect(screen.getByTestId("tab-RESOLVED")).toHaveTextContent("Resolved (0)");
    });

    it("shows zero counts when no items exist", () => {
      mockUsePredictions.mockReturnValue({
        data: createMockPredictionsData([]),
        isLoading: false,
        isError: false,
      });

      render(<TabPredictions />);

      expect(screen.getByTestId("tab-ALL")).toHaveTextContent("All (0)");
      expect(screen.getByTestId("tab-CRITICAL")).toHaveTextContent("Critical (0)");
      expect(screen.getByTestId("tab-URGENT")).toHaveTextContent("Urgent (0)");
      expect(screen.getByTestId("tab-ATTENTION")).toHaveTextContent("Attention (0)");
      expect(screen.getByTestId("tab-HEALTHY")).toHaveTextContent("Safe (0)");
    });
  });

  describe("filtering stale and well-stocked items", () => {
    it("excludes items with daysToStockout >= 30 (well-stocked threshold)", () => {
      const items = [
        createMockItem({ itemId: "u1", urgency: "URGENT", daysToStockout: 5 }),
        createMockItem({ itemId: "ws1", urgency: "HEALTHY", daysToStockout: 35 }),
      ];

      mockUsePredictions.mockReturnValue({
        data: createMockPredictionsData(items),
        isLoading: false,
        isError: false,
      });

      render(<TabPredictions />);

      // Only the non-well-stocked item should appear in the All count
      expect(screen.getByTestId("tab-ALL")).toHaveTextContent("All (1)");
    });

    it("excludes items with stale computedAt (older than 30 days)", () => {
      const staleDate = new Date(Date.now() - 31 * 24 * 60 * 60 * 1000).toISOString();
      const items = [
        createMockItem({ itemId: "fresh", urgency: "URGENT", daysToStockout: 5 }),
        createMockItem({ itemId: "stale", urgency: "CRITICAL", daysToStockout: 1, computedAt: staleDate }),
      ];

      mockUsePredictions.mockReturnValue({
        data: createMockPredictionsData(items),
        isLoading: false,
        isError: false,
      });

      render(<TabPredictions />);

      expect(screen.getByTestId("tab-ALL")).toHaveTextContent("All (1)");
    });
  });

  describe("rendering items", () => {
    it("renders prediction cards for active items", () => {
      const items = [
        createMockItem({ itemId: "p1", name: "Pocky", urgency: "CRITICAL", daysToStockout: 2 }),
        createMockItem({ itemId: "p2", name: "KitKat", urgency: "URGENT", daysToStockout: 5 }),
      ];

      mockUsePredictions.mockReturnValue({
        data: createMockPredictionsData(items),
        isLoading: false,
        isError: false,
      });

      render(<TabPredictions />);

      expect(screen.getByTestId("prediction-card-p1")).toHaveTextContent("Pocky");
      expect(screen.getByTestId("prediction-card-p2")).toHaveTextContent("KitKat");
    });
  });

  describe("dismissed items", () => {
    it("moves dismissed items to the resolved count", () => {
      const items = [
        createMockItem({ itemId: "keep", urgency: "URGENT", daysToStockout: 5, computedAt: "2026-03-30T00:00:00Z" }),
        createMockItem({ itemId: "dismissed", urgency: "CRITICAL", daysToStockout: 1, computedAt: "2026-03-30T00:00:00Z" }),
      ];

      mockUseDismissedPredictions.mockReturnValue({
        dismissedMap: {
          dismissed: { dismissedAt: Date.now(), computedAt: "2026-03-30T00:00:00Z" },
        },
        dismissedIds: new Set(["dismissed"]),
        dismiss: vi.fn(),
        restore: vi.fn(),
      });

      mockUsePredictions.mockReturnValue({
        data: createMockPredictionsData(items),
        isLoading: false,
        isError: false,
      });

      render(<TabPredictions />);

      expect(screen.getByTestId("tab-ALL")).toHaveTextContent("All (1)");
      expect(screen.getByTestId("tab-RESOLVED")).toHaveTextContent("Resolved (1)");
    });
  });
});
