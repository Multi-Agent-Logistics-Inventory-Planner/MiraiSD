"use client"

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
  AlertTriangle,
  DollarSign,
  TrendingUp,
  ShoppingCart,
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
import { stockPredictions, categoryDistribution } from "@/lib/data"

// Mock data for charts
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

const performanceMetrics = [
  { name: "Turnover Rate", value: 4.2, target: 5.0, unit: "x" },
  { name: "Forecast Accuracy", value: 87, target: 90, unit: "%" },
  { name: "Stockout Rate", value: 2.3, target: 2.0, unit: "%" },
  { name: "Fill Rate", value: 94, target: 95, unit: "%" },
]

export default function AnalyticsPage() {
  const itemsAtRisk = stockPredictions.filter((p) => p.daysUntilStockout <= 7).length
  const reorderValue = stockPredictions.reduce(
    (sum, p) => sum + p.recommendedOrder * 15,
    0
  )
  const salesTrend = 12.5

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
              <div className="text-2xl font-bold text-amber-600">{itemsAtRisk}</div>
              <p className="text-xs text-muted-foreground">
                Predicted stockout within 7 days
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium">Reorder Value</CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                ${reorderValue.toLocaleString()}
              </div>
              <p className="text-xs text-muted-foreground">
                Estimated cost for recommended orders
              </p>
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
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>SKU</TableHead>
                  <TableHead>Item Name</TableHead>
                  <TableHead>Current Stock</TableHead>
                  <TableHead>Daily Sales</TableHead>
                  <TableHead>Days Until Stockout</TableHead>
                  <TableHead>Recommended Order</TableHead>
                  <TableHead>Confidence</TableHead>
                  <TableHead>Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {stockPredictions.map((prediction) => (
                  <TableRow key={prediction.id}>
                    <TableCell className="font-mono text-sm">
                      {prediction.sku}
                    </TableCell>
                    <TableCell className="font-medium">
                      {prediction.name}
                    </TableCell>
                    <TableCell>{prediction.currentStock} units</TableCell>
                    <TableCell>{prediction.dailySales} units/day</TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={
                          prediction.daysUntilStockout <= 3
                            ? "bg-red-100 text-red-700"
                            : prediction.daysUntilStockout <= 7
                              ? "bg-amber-100 text-amber-700"
                              : "bg-green-100 text-green-700"
                        }
                      >
                        {prediction.daysUntilStockout} days
                      </Badge>
                    </TableCell>
                    <TableCell>{prediction.recommendedOrder} units</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Progress
                          value={prediction.confidence}
                          className="h-2 w-16"
                        />
                        <span className="text-sm text-muted-foreground">
                          {prediction.confidence}%
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
                ))}
              </TableBody>
            </Table>
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
              <div className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={categoryDistribution}
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
                      {categoryDistribution.map((entry, index) => (
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
            </CardContent>
          </Card>

          {/* Performance Metrics */}
          <Card>
            <CardHeader>
              <CardTitle>Performance Metrics</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              {performanceMetrics.map((metric) => (
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
              ))}
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  )
}
