import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ActionItem, PredictionsData } from "@/types/analytics";

const mockUsePredictions = vi.fn();
const mockUseDismissedPredictions = vi.fn();

vi.mock("@/hooks/queries/use-predictions", () => ({
  usePredictions: () => mockUsePredictions(),
}));

vi.mock("@/hooks/use-dismissed-predictions", () => ({
  useDismissedPredictions: () => mockUseDismissedPredictions(),
}));

vi.mock("@/components/analytics/predictions", async () => {
  const actual = await vi.importActual<typeof import("@/components/analytics/predictions")>(
    "@/components/analytics/predictions",
  );
  return {
    ...actual,
    TriageRow: ({ item }: { item: ActionItem }) => (
      <div data-testid={`triage-row-${item.itemId}`}>{item.name}</div>
    ),
    SummaryHead: ({ actionCount }: { actionCount: number }) => (
      <div data-testid="summary-head" data-count={actionCount} />
    ),
    UrgencyTabs: ({
      tabs,
      activeTab,
      onTabChange,
    }: {
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
    suggestedOrderDate: "2026-06-15",
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

  describe("urgency tab counts", () => {
    it("displays counts grouped by daysToStockout thresholds", () => {
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

      // Action needed = days <= 7 (CRITICAL + URGENT)
      expect(screen.getByTestId("tab-ACTION_NEEDED")).toHaveTextContent("Action Needed (3)");
      // Watch = 7 < days <= 14
      expect(screen.getByTestId("tab-WATCH")).toHaveTextContent("Watch (3)");
      // Healthy = days > 14 and < 30 (well-stocked threshold)
      expect(screen.getByTestId("tab-HEALTHY")).toHaveTextContent("Healthy (1)");
      expect(screen.getByTestId("tab-RESOLVED")).toHaveTextContent("Resolved (0)");
    });
  });

  describe("well-stocked filter", () => {
    it("excludes items with daysToStockout >= 30", () => {
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
      // Only the non-well-stocked URGENT item should appear in Action Needed
      expect(screen.getByTestId("tab-ACTION_NEEDED")).toHaveTextContent("Action Needed (1)");
      expect(screen.getByTestId("tab-HEALTHY")).toHaveTextContent("Healthy (0)");
    });
  });

  describe("rendering items", () => {
    it("renders triage rows for action-needed items by default", () => {
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

      expect(screen.getByTestId("triage-row-p1")).toHaveTextContent("Pocky");
      expect(screen.getByTestId("triage-row-p2")).toHaveTextContent("KitKat");
    });
  });

  describe("dismissed items", () => {
    it("moves dismissed items into the resolved tab count", () => {
      const items = [
        createMockItem({
          itemId: "keep",
          urgency: "URGENT",
          daysToStockout: 5,
          computedAt: "2026-03-30T00:00:00Z",
        }),
        createMockItem({
          itemId: "dismissed",
          urgency: "CRITICAL",
          daysToStockout: 1,
          computedAt: "2026-03-30T00:00:00Z",
        }),
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

      expect(screen.getByTestId("tab-ACTION_NEEDED")).toHaveTextContent("Action Needed (1)");
      expect(screen.getByTestId("tab-RESOLVED")).toHaveTextContent("Resolved (1)");
    });
  });
});
