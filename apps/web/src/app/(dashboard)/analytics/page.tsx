"use client"

import { useState } from "react"
import { DashboardHeader } from "@/components/dashboard-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Progress } from "@/components/ui/progress"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import {
  AlertTriangle,
  DollarSign,
  TrendingUp,
  ShoppingCart,
  Loader2,
} from "lucide-react"
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  Legend,
} from "recharts"
import { useForecasts, useAtRiskForecasts } from "@/hooks/queries/use-forecasts"
import { useInventoryByCategory, usePerformanceMetrics } from "@/hooks/queries/use-analytics"
import { format } from "date-fns"

// Mock data for charts (kept as placeholder until we have chart API)
const stockLevelData = [
  { name: "Jan", actual: 400, predicted: 380 },
  { name: "Feb", actual: 300, predicted: 320 },
  { name: "Mar", actual: 450, predicted: 420 },
  { name: "Apr", actual: 280, predicted: 300 },
  { name: "May", actual: 390, predicted: 380 },
  { name: "Jun", actual: 430, predicted: 450 },
]

const salesForecastData = [
  { name: "Week 1", actual: 120, predicted: 115 },
  { name: "Week 2", actual: 98, predicted: 105 },
  { name: "Week 3", actual: 135, predicted: 130 },
  { name: "Week 4", actual: 110, predicted: 120 },
]

// Colors for pie chart
const COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
]

export default function AnalyticsPage() {
  const [page, setPage] = useState(1)
  const pageSize = 10

  // Fetch paginated forecasts
  const { 
    data: forecastData, 
    isLoading: isLoadingForecasts,
    isError: isForecastError 
  } = useForecasts(page, pageSize)

  // Fetch at-risk items for "Items at Risk" and "Reorder Value" calculation
  const { 
    data: atRiskData, 
    isLoading: isLoadingAtRisk 
  } = useAtRiskForecasts(7) // 7 days threshold

  // Fetch Inventory by Category
  const {
      data: categoryData,
      isLoading: isLoadingCategory
  } = useInventoryByCategory()

  // Fetch Performance Metrics
  const {
      data: metricsData,
      isLoading: isLoadingMetrics
  } = usePerformanceMetrics()

  const itemsAtRisk = atRiskData?.length ?? 0
  
  // Calculate total reorder value from at-risk items
  const reorderValue = atRiskData?.reduce(
    (sum, p) => sum + (p.suggestedReorderQty * (p.unitCost ?? 0)),
    0
  ) ?? 0

  const salesTrend = 12.5 // Placeholder

  const handlePageChange = (newPage: number) => {
    if (newPage >= 1 && newPage <= (forecastData?.totalPages ?? 1)) {
      setPage(newPage)
    }
  }
  
  // Transform category data for chart
  const categoryChartData = categoryData?.map((item, index) => ({
      name: item.category,
      value: item.totalStock,
      fill: COLORS[index % COLORS.length]
  })) || []

  // Transform metrics data for display
  const metrics = [
      { name: "Turnover Rate", value: metricsData?.turnoverRate ?? 0, target: 5.0, unit: "x" },
      { name: "Forecast Accuracy", value: metricsData?.forecastAccuracy ?? 0, target: 90, unit: "%" },
      { name: "Stockout Rate", value: metricsData?.stockoutRate ?? 0, target: 2.0, unit: "%" },
      { name: "Fill Rate", value: metricsData?.fillRate ?? 0, target: 95, unit: "%" },
  ]

  return (
    <div className="flex flex-col">
      <DashboardHeader
        title="Analytics"
        description="Stock predictions and forecasting"
      />
      <main className="flex-1 space-y-6 p-4 md:p-6">
        {/* Stats Cards */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Items at Risk</CardTitle>
              <AlertTriangle className="h-4 w-4 text-amber-500" />
            </CardHeader>
            <CardContent>
              {isLoadingAtRisk ? (
                 <div className="h-8 w-16 animate-pulse rounded bg-muted" />
              ) : (
                <>
                  <div className="text-2xl font-bold text-amber-600">{itemsAtRisk}</div>
                  <p className="text-xs text-muted-foreground">
                    Predicted stockout within 7 days
                  </p>
                </>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Reorder Value</CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              {isLoadingAtRisk ? (
                <div className="h-8 w-24 animate-pulse rounded bg-muted" />
              ) : (
                <>
                  <div className="text-2xl font-bold">
                    ${reorderValue.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Estimated cost for urgent orders
                  </p>
                </>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Sales Trend</CardTitle>
              <TrendingUp className="h-4 w-4 text-green-500" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">
                +{salesTrend}%
              </div>
              <p className="text-xs text-muted-foreground">
                Compared to last month
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Stock Predictions Table */}
        <Card>
          <CardHeader>
            <CardTitle>AI Stock Predictions</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoadingForecasts ? (
              <div className="flex h-[300px] items-center justify-center">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
              </div>
            ) : isForecastError ? (
              <div className="flex h-[150px] items-center justify-center text-red-500">
                Failed to load predictions
              </div>
            ) : (
              <div className="space-y-4">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>SKU</TableHead>
                      <TableHead>Item Name</TableHead>
                      <TableHead>Days Until Stockout</TableHead>
                      <TableHead>Recommended Order</TableHead>
                      <TableHead>Order By</TableHead>
                      <TableHead>Confidence</TableHead>
                      <TableHead>Action</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {forecastData?.content?.length === 0 ? (
                       <TableRow>
                         <TableCell colSpan={7} className="text-center text-muted-foreground">
                           No predictions available
                         </TableCell>
                       </TableRow>
                    ) : (
                      forecastData?.content?.map((prediction) => (
                        <TableRow key={prediction.id}>
                          <TableCell className="font-mono text-sm">
                            {prediction.itemSku}
                          </TableCell>
                          <TableCell className="font-medium">
                            {prediction.itemName}
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant="outline"
                              className={
                                prediction.daysToStockout <= 3
                                  ? "bg-red-100 text-red-700"
                                  : prediction.daysToStockout <= 7
                                    ? "bg-amber-100 text-amber-700"
                                    : "bg-green-100 text-green-700"
                              }
                            >
                              {prediction.daysToStockout} days
                            </Badge>
                          </TableCell>
                          <TableCell>{prediction.suggestedReorderQty} units</TableCell>
                          <TableCell>
                            {prediction.suggestedOrderDate ? 
                              format(new Date(prediction.suggestedOrderDate), 'MMM d, yyyy') : 
                              '-'}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <Progress
                                value={(prediction.confidence ?? 0) * 100}
                                className="h-2 w-16"
                              />
                              <span className="text-sm text-muted-foreground">
                                {((prediction.confidence ?? 0) * 100).toFixed(0)}%
                              </span>
                            </div>
                          </TableCell>
                          <TableCell>
                            <Button size="sm">
                              <ShoppingCart className="mr-2 h-3 w-3" />
                              Order
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
                
                {/* Pagination */}
                {forecastData && forecastData.totalPages > 1 && (
                  <Pagination>
                    <PaginationContent>
                      <PaginationItem>
                        <PaginationPrevious 
                          href="#" 
                          onClick={(e) => { e.preventDefault(); handlePageChange(page - 1); }}
                          className={page <= 1 ? "pointer-events-none opacity-50" : ""}
                        />
                      </PaginationItem>
                      
                      {/* Show current page and simplified ranges */}
                      <PaginationItem>
                        <PaginationLink href="#" isActive>
                          {page}
                        </PaginationLink>
                      </PaginationItem>
                      
                      <PaginationItem>
                        <PaginationNext 
                          href="#" 
                          onClick={(e) => { e.preventDefault(); handlePageChange(page + 1); }}
                          className={page >= forecastData.totalPages ? "pointer-events-none opacity-50" : ""}
                        />
                      </PaginationItem>
                    </PaginationContent>
                  </Pagination>
                )}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Charts Row */}
        <div className="grid gap-6 md:grid-cols-2">
          {/* Stock Level vs Prediction */}
          <Card>
            <CardHeader>
              <CardTitle>Stock Level vs Prediction</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={stockLevelData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Line
                      type="monotone"
                      dataKey="actual"
                      stroke="var(--chart-1)"
                      strokeWidth={2}
                      name="Actual Stock"
                    />
                    <Line
                      type="monotone"
                      dataKey="predicted"
                      stroke="var(--chart-2)"
                      strokeWidth={2}
                      strokeDasharray="5 5"
                      name="Predicted Stock"
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          {/* Sales Forecast */}
          <Card>
            <CardHeader>
              <CardTitle>Sales Forecast (Actual vs Predicted)</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={salesForecastData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="actual" fill="var(--chart-1)" name="Actual Sales" />
                    <Bar
                      dataKey="predicted"
                      fill="var(--chart-3)"
                      name="Predicted Sales"
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Second Charts Row */}
        <div className="grid gap-6 md:grid-cols-2">
          {/* Inventory Pie Chart */}
          <Card>
            <CardHeader>
              <CardTitle>Inventory by Category</CardTitle>
            </CardHeader>
            <CardContent>
              {isLoadingCategory ? (
                 <div className="flex h-[300px] items-center justify-center">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                 </div>
              ) : (
                <div className="h-[300px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={categoryChartData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={100}
                        paddingAngle={5}
                        dataKey="value"
                        label={({ name, percent }) =>
                          `${name} ${(percent * 100).toFixed(0)}%`
                        }
                      >
                        {categoryChartData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.fill} />
                        ))}
                      </Pie>
                      <Tooltip
                        formatter={(value: number) => [`${value} units`, "Quantity"]}
                      />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Performance Metrics */}
          <Card>
            <CardHeader>
              <CardTitle>Performance Metrics</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              {isLoadingMetrics ? (
                <div className="space-y-4">
                    <div className="h-4 w-full animate-pulse rounded bg-muted"></div>
                    <div className="h-4 w-full animate-pulse rounded bg-muted"></div>
                    <div className="h-4 w-full animate-pulse rounded bg-muted"></div>
                    <div className="h-4 w-full animate-pulse rounded bg-muted"></div>
                </div>
              ) : (
                metrics.map((metric) => (
                    <div key={metric.name} className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium">{metric.name}</span>
                        <span className="text-sm text-muted-foreground">
                          {metric.value}
                          {metric.unit} / {metric.target}
                          {metric.unit}
                        </span>
                      </div>
                      <Progress
                        value={(metric.value / metric.target) * 100}
                        className={
                          metric.value >= metric.target
                            ? "[&>[data-slot=progress-indicator]]:bg-green-500"
                            : "[&>[data-slot=progress-indicator]]:bg-amber-500"
                        }
                      />
                    </div>
                ))
              )}
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  )
}
