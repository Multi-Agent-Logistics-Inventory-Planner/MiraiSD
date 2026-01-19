"use client"

import { DollarSign, AlertTriangle, PackageX, Package } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"

interface StatCardsProps {
  totalStockValue: number
  stockValueChange: number
  lowStockItems: number
  criticalItems: number
  outOfStockItems: number
  totalSKUs: number
}

export function StatCards({
  totalStockValue,
  stockValueChange,
  lowStockItems,
  criticalItems,
  outOfStockItems,
  totalSKUs,
}: StatCardsProps) {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Total Stock Value</CardTitle>
          <DollarSign className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            ${totalStockValue.toLocaleString()}
          </div>
          <p className="text-xs text-muted-foreground">
            <span className={stockValueChange >= 0 ? "text-green-600" : "text-red-600"}>
              {stockValueChange >= 0 ? "+" : ""}{stockValueChange}%
            </span>{" "}
            from last month
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Low Stock Items</CardTitle>
          <AlertTriangle className="h-4 w-4 text-amber-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{lowStockItems}</div>
          <p className="text-xs text-muted-foreground">Items requiring attention</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Critical Items</CardTitle>
          <PackageX className="h-4 w-4 text-red-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold text-red-600">
            {criticalItems + outOfStockItems}
          </div>
          <p className="text-xs text-muted-foreground">
            {criticalItems} critical, {outOfStockItems} out of stock
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Total SKUs</CardTitle>
          <Package className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{totalSKUs}</div>
          <p className="text-xs text-muted-foreground">Unique items tracked</p>
        </CardContent>
      </Card>
    </div>
  )
}
