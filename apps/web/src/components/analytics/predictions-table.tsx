"use client"

import { useState } from "react"
import {
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Download,
  ShoppingCart,
  Search,
  Filter,
  Loader2,
} from "lucide-react"
import { format } from "date-fns"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import type { ForecastPrediction, PaginatedResponse } from "@/types/api"

type SortField = "itemSku" | "itemName" | "currentStock" | "avgDailyDelta" | "daysToStockout" | "suggestedReorderQty" | "suggestedOrderDate" | "confidence"
type SortDirection = "asc" | "desc"
type RiskFilter = "all" | "critical" | "warning" | "safe"

interface PredictionsTableProps {
  data: PaginatedResponse<ForecastPrediction> | undefined
  isLoading: boolean
  isError: boolean
  page: number
  pageSize: number
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
  onOrder?: (item: ForecastPrediction) => void
}

function getDaysToStockoutBadgeClass(days: number): string {
  if (days <= 3) return "bg-red-100 text-red-700 border-red-200"
  if (days <= 7) return "bg-amber-100 text-amber-700 border-amber-200"
  if (days <= 14) return "bg-yellow-100 text-yellow-700 border-yellow-200"
  return "bg-green-100 text-green-700 border-green-200"
}

function sanitizeCSVField(value: string): string {
  const escaped = value.replace(/"/g, '""')
  const formulaChars = ["=", "+", "-", "@", "\t", "\r"]
  if (formulaChars.some((char) => escaped.startsWith(char))) {
    return `"'${escaped}"`
  }
  return `"${escaped}"`
}

interface SortIconProps {
  field: SortField
  currentSortField: SortField
  sortDirection: SortDirection
}

function SortIcon({ field, currentSortField, sortDirection }: SortIconProps) {
  if (currentSortField !== field) {
    return <ArrowUpDown className="ml-1 h-3 w-3 text-muted-foreground" />
  }
  return sortDirection === "asc" ? (
    <ArrowUp className="ml-1 h-3 w-3" />
  ) : (
    <ArrowDown className="ml-1 h-3 w-3" />
  )
}

function getAriaSort(field: SortField, currentSortField: SortField, sortDirection: SortDirection): "ascending" | "descending" | "none" {
  if (currentSortField !== field) return "none"
  return sortDirection === "asc" ? "ascending" : "descending"
}

function filterAndSortData(
  content: ForecastPrediction[] | undefined,
  searchTerm: string,
  riskFilter: RiskFilter,
  sortField: SortField,
  sortDirection: SortDirection
): ForecastPrediction[] {
  if (!content) return []

  let filtered = [...content]

  if (searchTerm) {
    const term = searchTerm.toLowerCase()
    filtered = filtered.filter(
      (item) =>
        item.itemSku.toLowerCase().includes(term) ||
        item.itemName.toLowerCase().includes(term)
    )
  }

  if (riskFilter !== "all") {
    filtered = filtered.filter((item) => {
      switch (riskFilter) {
        case "critical":
          return item.daysToStockout <= 3
        case "warning":
          return item.daysToStockout > 3 && item.daysToStockout <= 7
        case "safe":
          return item.daysToStockout > 7
        default:
          return true
      }
    })
  }

  filtered.sort((a, b) => {
    let aVal: string | number
    let bVal: string | number

    switch (sortField) {
      case "itemSku":
        aVal = a.itemSku
        bVal = b.itemSku
        break
      case "itemName":
        aVal = a.itemName
        bVal = b.itemName
        break
      case "currentStock":
        aVal = a.currentStock
        bVal = b.currentStock
        break
      case "avgDailyDelta":
        aVal = a.avgDailyDelta
        bVal = b.avgDailyDelta
        break
      case "daysToStockout":
        aVal = a.daysToStockout
        bVal = b.daysToStockout
        break
      case "suggestedReorderQty":
        aVal = a.suggestedReorderQty
        bVal = b.suggestedReorderQty
        break
      case "suggestedOrderDate":
        aVal = a.suggestedOrderDate || ""
        bVal = b.suggestedOrderDate || ""
        break
      case "confidence":
        aVal = a.confidence
        bVal = b.confidence
        break
      default:
        return 0
    }

    if (typeof aVal === "string" && typeof bVal === "string") {
      return sortDirection === "asc"
        ? aVal.localeCompare(bVal)
        : bVal.localeCompare(aVal)
    }

    return sortDirection === "asc"
      ? (aVal as number) - (bVal as number)
      : (bVal as number) - (aVal as number)
  })

  return filtered
}

export function PredictionsTable({
  data,
  isLoading,
  isError,
  page,
  pageSize,
  onPageChange,
  onPageSizeChange,
  onOrder,
}: PredictionsTableProps) {
  const [searchTerm, setSearchTerm] = useState("")
  const [riskFilter, setRiskFilter] = useState<RiskFilter>("all")
  const [sortField, setSortField] = useState<SortField>("daysToStockout")
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc")

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === "asc" ? "desc" : "asc")
    } else {
      setSortField(field)
      setSortDirection("asc")
    }
  }

  const filteredAndSortedData = filterAndSortData(
    data?.content,
    searchTerm,
    riskFilter,
    sortField,
    sortDirection
  )

  const handleExportCSV = () => {
    if (!data?.content || data.content.length === 0) return

    const headers = [
      "SKU",
      "Item Name",
      "Current Stock",
      "Avg Daily Demand",
      "Days to Stockout",
      "Reorder Qty",
      "Order By",
      "Confidence",
    ]

    const rows = filteredAndSortedData.map((item) => [
      sanitizeCSVField(item.itemSku),
      sanitizeCSVField(item.itemName),
      item.currentStock,
      item.avgDailyDelta.toFixed(2),
      item.daysToStockout,
      item.suggestedReorderQty,
      item.suggestedOrderDate
        ? format(new Date(item.suggestedOrderDate), "yyyy-MM-dd")
        : "",
      `${(item.confidence * 100).toFixed(0)}%`,
    ])

    const csvContent = [headers.join(","), ...rows.map((row) => row.join(","))].join("\n")

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" })
    const link = document.createElement("a")
    const url = URL.createObjectURL(blob)
    link.setAttribute("href", url)
    link.setAttribute("download", `stock-predictions-${format(new Date(), "yyyy-MM-dd")}.csv`)
    link.style.visibility = "hidden"
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  return (
    <Card>
      <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <CardTitle>AI Stock Predictions</CardTitle>
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search SKU or name..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-[200px] pl-8"
            />
          </div>
          <Select value={riskFilter} onValueChange={(v) => setRiskFilter(v as RiskFilter)}>
            <SelectTrigger className="w-[140px]">
              <Filter className="mr-2 h-4 w-4" />
              <SelectValue placeholder="Risk Level" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Items</SelectItem>
              <SelectItem value="critical">Critical (&lt;3 days)</SelectItem>
              <SelectItem value="warning">Warning (3-7 days)</SelectItem>
              <SelectItem value="safe">Safe (&gt;7 days)</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={handleExportCSV} disabled={!data?.content?.length}>
            <Download className="mr-2 h-4 w-4" />
            Export CSV
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex h-[300px] items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        ) : isError ? (
          <div className="flex h-[150px] items-center justify-center text-red-500">
            Failed to load predictions
          </div>
        ) : (
          <div className="space-y-4">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead aria-sort={getAriaSort("itemSku", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("itemSku")}>
                        SKU
                        <SortIcon field="itemSku" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead aria-sort={getAriaSort("itemName", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("itemName")}>
                        Item Name
                        <SortIcon field="itemName" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead aria-sort={getAriaSort("currentStock", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("currentStock")}>
                        Stock
                        <SortIcon field="currentStock" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead aria-sort={getAriaSort("avgDailyDelta", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("avgDailyDelta")}>
                        Daily Demand
                        <SortIcon field="avgDailyDelta" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead aria-sort={getAriaSort("daysToStockout", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("daysToStockout")}>
                        Days to Stockout
                        <SortIcon field="daysToStockout" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead aria-sort={getAriaSort("suggestedReorderQty", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("suggestedReorderQty")}>
                        Reorder Qty
                        <SortIcon field="suggestedReorderQty" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead aria-sort={getAriaSort("suggestedOrderDate", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("suggestedOrderDate")}>
                        Order By
                        <SortIcon field="suggestedOrderDate" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead aria-sort={getAriaSort("confidence", sortField, sortDirection)}>
                      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => handleSort("confidence")}>
                        Confidence
                        <SortIcon field="confidence" currentSortField={sortField} sortDirection={sortDirection} />
                      </Button>
                    </TableHead>
                    <TableHead>Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredAndSortedData.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={9} className="text-center text-muted-foreground">
                        {searchTerm || riskFilter !== "all"
                          ? "No items match your filters"
                          : "No predictions available"}
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredAndSortedData.map((prediction) => (
                      <TableRow key={prediction.id}>
                        <TableCell className="font-mono text-sm">
                          {prediction.itemSku}
                        </TableCell>
                        <TableCell className="max-w-[200px] truncate font-medium">
                          {prediction.itemName}
                        </TableCell>
                        <TableCell>{prediction.currentStock}</TableCell>
                        <TableCell>
                          {Math.abs(prediction.avgDailyDelta).toFixed(1)}/day
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant="outline"
                            className={getDaysToStockoutBadgeClass(prediction.daysToStockout)}
                          >
                            {prediction.daysToStockout} days
                          </Badge>
                        </TableCell>
                        <TableCell>{prediction.suggestedReorderQty} units</TableCell>
                        <TableCell>
                          {prediction.suggestedOrderDate
                            ? format(new Date(prediction.suggestedOrderDate), "MMM d, yyyy")
                            : "-"}
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <Progress
                              value={prediction.confidence * 100}
                              className="h-2 w-16"
                            />
                            <span className="text-sm text-muted-foreground">
                              {(prediction.confidence * 100).toFixed(0)}%
                            </span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Button
                            size="sm"
                            onClick={() => onOrder?.(prediction)}
                          >
                            <ShoppingCart className="mr-2 h-3 w-3" />
                            Order
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>

            <div className="flex flex-col items-center justify-between gap-4 sm:flex-row">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <span>Rows per page:</span>
                <Select
                  value={pageSize.toString()}
                  onValueChange={(v) => onPageSizeChange(Number(v))}
                >
                  <SelectTrigger className="h-8 w-[70px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="10">10</SelectItem>
                    <SelectItem value="25">25</SelectItem>
                    <SelectItem value="50">50</SelectItem>
                  </SelectContent>
                </Select>
                <span>
                  {data ? `${(page - 1) * pageSize + 1}-${Math.min(page * pageSize, data.totalElements)} of ${data.totalElements}` : ""}
                </span>
              </div>

              {data && data.totalPages > 1 && (
                <Pagination>
                  <PaginationContent>
                    <PaginationItem>
                      <PaginationPrevious
                        href="#"
                        onClick={(e) => {
                          e.preventDefault()
                          onPageChange(page - 1)
                        }}
                        className={page <= 1 ? "pointer-events-none opacity-50" : ""}
                      />
                    </PaginationItem>

                    {Array.from({ length: Math.min(5, data.totalPages) }, (_, i) => {
                      let pageNum: number
                      if (data.totalPages <= 5) {
                        pageNum = i + 1
                      } else if (page <= 3) {
                        pageNum = i + 1
                      } else if (page >= data.totalPages - 2) {
                        pageNum = data.totalPages - 4 + i
                      } else {
                        pageNum = page - 2 + i
                      }
                      return (
                        <PaginationItem key={pageNum}>
                          <PaginationLink
                            href="#"
                            isActive={page === pageNum}
                            onClick={(e) => {
                              e.preventDefault()
                              onPageChange(pageNum)
                            }}
                          >
                            {pageNum}
                          </PaginationLink>
                        </PaginationItem>
                      )
                    })}

                    <PaginationItem>
                      <PaginationNext
                        href="#"
                        onClick={(e) => {
                          e.preventDefault()
                          onPageChange(page + 1)
                        }}
                        className={page >= data.totalPages ? "pointer-events-none opacity-50" : ""}
                      />
                    </PaginationItem>
                  </PaginationContent>
                </Pagination>
              )}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
