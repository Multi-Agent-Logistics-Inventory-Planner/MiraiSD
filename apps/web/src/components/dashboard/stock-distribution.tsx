"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from "recharts"
import { Skeleton } from "@/components/ui/skeleton"

interface CategoryData {
  name: string
  value: number
  fill: string
}

interface StockDistributionProps {
  data: CategoryData[]
  isLoading?: boolean
}

export function StockDistribution({ data, isLoading }: StockDistributionProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>Stock Distribution by Category</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[250px]">
          {isLoading ? (
            <div className="h-full w-full flex items-center justify-center">
              <div className="space-y-3">
                <Skeleton className="h-6 w-48" />
                <Skeleton className="h-48 w-48 rounded-full" />
              </div>
            </div>
          ) : data.length === 0 ? (
            <p className="text-sm text-muted-foreground">No category data yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data}
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={80}
                  paddingAngle={5}
                  dataKey="value"
                  label={({ name, percent }) =>
                    `${name} ${(percent * 100).toFixed(0)}%`
                  }
                  labelLine={false}
                >
                  {data.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.fill} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value: number) => [`${value} units`, "Quantity"]}
                />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
