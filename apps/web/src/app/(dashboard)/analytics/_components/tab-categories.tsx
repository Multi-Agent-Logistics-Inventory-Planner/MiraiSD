"use client"

import { useMemo } from "react"
import { Package, Layers, BarChart3, PackageX } from "lucide-react"
import { CategoryChart } from "@/components/analytics"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  DataTableHeader,
  TableHead,
  TableRow,
} from "@/components/ui/table"
import { Skeleton } from "@/components/ui/skeleton"
import type { CategoryInventory } from "@/types/api"

interface TabCategoriesProps {
  categoryData: CategoryInventory[] | undefined
  isLoadingCategory: boolean
}

interface CategoryRow {
  category: string
  totalItems: number
  totalStock: number
  sharePercent: number
  isEmpty: boolean
}

interface SummaryStats {
  totalCategories: number
  activeCategories: number
  totalItems: number
  totalStock: number
}

function computeSummary(data: CategoryInventory[]): SummaryStats {
  return {
    totalCategories: data.length,
    activeCategories: data.filter((c) => c.totalStock > 0).length,
    totalItems: data.reduce((sum, c) => sum + c.totalItems, 0),
    totalStock: data.reduce((sum, c) => sum + c.totalStock, 0),
  }
}

function buildTableRows(data: CategoryInventory[]): CategoryRow[] {
  const totalStock = data.reduce((sum, c) => sum + c.totalStock, 0)
  const stocked = [...data]
    .filter((c) => c.totalStock > 0)
    .sort((a, b) => b.totalStock - a.totalStock)
  const empty = [...data]
    .filter((c) => c.totalStock === 0)
    .sort((a, b) => a.category.localeCompare(b.category))

  return [...stocked, ...empty].map((c) => ({
    category: c.category,
    totalItems: c.totalItems,
    totalStock: c.totalStock,
    sharePercent: totalStock > 0 ? (c.totalStock / totalStock) * 100 : 0,
    isEmpty: c.totalStock === 0,
  }))
}

interface StatCardProps {
  label: string
  value: string | number
  icon: React.ComponentType<{ className?: string }>
  subtext?: string
}

function StatCard({ label, value, icon: Icon, subtext }: StatCardProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
            <Icon className="h-5 w-5 text-primary" />
          </div>
          <div className="min-w-0">
            <p className="text-sm text-muted-foreground">{label}</p>
            <p className="text-2xl font-semibold tabular-nums leading-tight">
              {typeof value === "number" ? value.toLocaleString() : value}
            </p>
            {subtext && (
              <p className="text-xs text-muted-foreground/70">{subtext}</p>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

interface ShareBarProps {
  percent: number
}

function ShareBar({ percent }: ShareBarProps) {
  return (
    <div className="flex items-center gap-2">
      <div className="h-2 w-16 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-primary/70 transition-all"
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>
      <span className="tabular-nums">{percent.toFixed(1)}%</span>
    </div>
  )
}

export function TabCategories({
  categoryData,
  isLoadingCategory,
}: TabCategoriesProps) {
  const tableRows = useMemo(
    () => (categoryData ? buildTableRows(categoryData) : []),
    [categoryData],
  )

  const summary = useMemo(
    () => (categoryData ? computeSummary(categoryData) : null),
    [categoryData],
  )

  const emptyCount = useMemo(
    () => tableRows.filter((r) => r.isEmpty).length,
    [tableRows],
  )

  const hasStocked = useMemo(
    () => tableRows.some((r) => !r.isEmpty),
    [tableRows],
  )

  if (isLoadingCategory) {
    return (
      <div className="space-y-6">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i}>
              <CardContent className="pt-6">
                <Skeleton className="h-16 w-full" />
              </CardContent>
            </Card>
          ))}
        </div>
        <Skeleton className="h-[300px] w-full rounded-xl" />
        <Skeleton className="h-[200px] w-full rounded-xl" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {summary && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Total Categories"
            value={summary.totalCategories}
            icon={Layers}
          />
          <StatCard
            label="Active Categories"
            value={summary.activeCategories}
            icon={BarChart3}
            subtext={`${summary.totalCategories - summary.activeCategories} empty`}
          />
          <StatCard
            label="Total Items"
            value={summary.totalItems}
            icon={Package}
            subtext="Across all categories"
          />
          <StatCard
            label="Total Stock"
            value={summary.totalStock}
            icon={Package}
            subtext="Units in inventory"
          />
        </div>
      )}

      <CategoryChart data={categoryData} isLoading={isLoadingCategory} />

      <Card>
        <CardHeader>
          <CardTitle>Category Breakdown</CardTitle>
        </CardHeader>
        <CardContent>
          {tableRows.length === 0 ? (
            <div className="flex h-[100px] items-center justify-center text-muted-foreground">
              No category data available
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <DataTableHeader>
                  <TableHead className="rounded-l-lg">Category</TableHead>
                  <TableHead className="text-right">Items</TableHead>
                  <TableHead className="text-right">Stock</TableHead>
                  <TableHead className="rounded-r-lg text-right">Share</TableHead>
                </DataTableHeader>
                <TableBody>
                  {tableRows.map((row, index) => {
                    const isFirstEmpty = row.isEmpty && (index === 0 || !tableRows[index - 1].isEmpty)

                    return (
                      <TableRow
                        key={row.category}
                        className={row.isEmpty ? "opacity-50" : undefined}
                      >
                        <TableCell className="rounded-l-lg font-medium">
                          <div className="flex items-center gap-2">
                            {isFirstEmpty && hasStocked && (
                              <PackageX className="h-3.5 w-3.5 text-muted-foreground" />
                            )}
                            {row.isEmpty && !isFirstEmpty && (
                              <span className="inline-block w-3.5" />
                            )}
                            {row.category}
                          </div>
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {row.totalItems}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {row.totalStock.toLocaleString()}
                        </TableCell>
                        <TableCell className="rounded-r-lg text-right">
                          {row.isEmpty ? (
                            <span className="text-muted-foreground">--</span>
                          ) : (
                            <ShareBar percent={row.sharePercent} />
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}

                  {emptyCount > 0 && hasStocked && (
                    <TableRow>
                      <TableCell
                        colSpan={4}
                        className="py-1.5 text-center text-xs text-muted-foreground/60"
                      >
                        {emptyCount} categor{emptyCount === 1 ? "y" : "ies"} with no stock
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
