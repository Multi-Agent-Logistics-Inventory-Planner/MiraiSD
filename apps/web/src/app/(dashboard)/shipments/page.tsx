"use client"

import { useState } from "react"
import { Suspense } from "react"
import {
  Plus,
  Search,
  Filter,
  Upload,
  Truck,
  Package,
  AlertTriangle,
} from "lucide-react"
import { DashboardHeader } from "@/components/dashboard-header"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { shipments, type Shipment } from "@/lib/data"
import { cn } from "@/lib/utils"

function getStatusColor(status: Shipment["status"]) {
  switch (status) {
    case "delivered":
      return "bg-green-100 text-green-700"
    case "in-transit":
      return "bg-blue-100 text-blue-700"
    case "delayed":
      return "bg-red-100 text-red-700"
    case "pending":
      return "bg-amber-100 text-amber-700"
    default:
      return "bg-gray-100 text-gray-700"
  }
}

function formatStatus(status: Shipment["status"]) {
  return status
    .split("-")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ")
}

export default function ShipmentsPage() {
  const [searchQuery, setSearchQuery] = useState("")
  const [typeFilter, setTypeFilter] = useState("all")
  const [statusFilter, setStatusFilter] = useState("all")
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false)

  const filteredShipments = shipments.filter((shipment) => {
    const matchesSearch =
      shipment.shipmentNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
      shipment.supplier?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      shipment.customer?.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesType = typeFilter === "all" || shipment.type === typeFilter
    const matchesStatus =
      statusFilter === "all" || shipment.status === statusFilter
    return matchesSearch && matchesType && matchesStatus
  })

  const totalShipments = shipments.length
  const inTransit = shipments.filter((s) => s.status === "in-transit").length
  const delivered = shipments.filter((s) => s.status === "delivered").length
  const delayed = shipments.filter((s) => s.status === "delayed").length

  return (
    <div className="flex flex-col">
        <DashboardHeader
          title="Shipments"
          description="Manage inbound and outbound shipments"
        />
        <main className="flex-1 space-y-6 p-4 md:p-6">
          {/* Stats Cards */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">
                  Total Shipments
                </CardTitle>
                <Package className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{totalShipments}</div>
                <p className="text-xs text-muted-foreground">This month</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">In Transit</CardTitle>
                <Truck className="h-4 w-4 text-blue-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-blue-600">{inTransit}</div>
                <p className="text-xs text-muted-foreground">Currently shipping</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">Delivered</CardTitle>
                <Package className="h-4 w-4 text-green-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-green-600">{delivered}</div>
                <p className="text-xs text-muted-foreground">Completed</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">Delayed</CardTitle>
                <AlertTriangle className="h-4 w-4 text-red-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-red-600">{delayed}</div>
                <p className="text-xs text-muted-foreground">Requires attention</p>
              </CardContent>
            </Card>
          </div>

          {/* Filters and Actions */}
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-1 items-center gap-2">
              <div className="relative flex-1 max-w-sm">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search shipments..."
                  className="pl-8"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
              <Select value={typeFilter} onValueChange={setTypeFilter}>
                <SelectTrigger className="w-[130px]">
                  <Filter className="mr-2 h-4 w-4" />
                  <SelectValue placeholder="Type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Types</SelectItem>
                  <SelectItem value="inbound">Inbound</SelectItem>
                  <SelectItem value="outbound">Outbound</SelectItem>
                </SelectContent>
              </Select>
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="w-[130px]">
                  <SelectValue placeholder="Status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Status</SelectItem>
                  <SelectItem value="pending">Pending</SelectItem>
                  <SelectItem value="in-transit">In Transit</SelectItem>
                  <SelectItem value="delivered">Delivered</SelectItem>
                  <SelectItem value="delayed">Delayed</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline">
                <Upload className="mr-2 h-4 w-4" />
                Upload Receipt
              </Button>
              <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
                <DialogTrigger asChild>
                  <Button>
                    <Plus className="mr-2 h-4 w-4" />
                    New Shipment
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Create New Shipment</DialogTitle>
                    <DialogDescription>
                      Add a new shipment record to the system.
                    </DialogDescription>
                  </DialogHeader>
                  <div className="grid gap-4 py-4">
                    <div className="grid gap-2">
                      <Label htmlFor="shipmentType">Shipment Type</Label>
                      <Select>
                        <SelectTrigger>
                          <SelectValue placeholder="Select type" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="inbound">Inbound</SelectItem>
                          <SelectItem value="outbound">Outbound</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="grid gap-2">
                      <Label htmlFor="supplierCustomer">Supplier / Customer</Label>
                      <Input id="supplierCustomer" placeholder="Enter name" />
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                      <div className="grid gap-2">
                        <Label htmlFor="items">Number of Items</Label>
                        <Input id="items" type="number" placeholder="0" />
                      </div>
                      <div className="grid gap-2">
                        <Label htmlFor="date">Shipment Date</Label>
                        <Input id="date" type="date" />
                      </div>
                    </div>
                    <div className="grid gap-2">
                      <Label htmlFor="estimatedDelivery">Estimated Delivery</Label>
                      <Input id="estimatedDelivery" type="date" />
                    </div>
                  </div>
                  <DialogFooter>
                    <Button
                      variant="outline"
                      onClick={() => setIsAddDialogOpen(false)}
                    >
                      Cancel
                    </Button>
                    <Button onClick={() => setIsAddDialogOpen(false)}>
                      Create Shipment
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>
          </div>

          {/* Drag and Drop Zone */}
          <Card className="border-dashed">
            <CardContent className="flex flex-col items-center justify-center py-8">
              <Upload className="h-10 w-10 text-muted-foreground mb-4" />
              <p className="text-sm text-muted-foreground mb-2">
                Drag and drop shipment files here
              </p>
              <p className="text-xs text-muted-foreground">
                Supports PDF, CSV, and Excel files
              </p>
            </CardContent>
          </Card>

          {/* Shipments Table */}
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Shipment #</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Supplier / Customer</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Items</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Est. Delivery</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredShipments.map((shipment) => (
                  <TableRow key={shipment.id}>
                    <TableCell className="font-mono text-sm">
                      {shipment.shipmentNumber}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={cn(
                          shipment.type === "inbound"
                            ? "bg-purple-100 text-purple-700"
                            : "bg-cyan-100 text-cyan-700"
                        )}
                      >
                        {shipment.type === "inbound" ? "Inbound" : "Outbound"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {shipment.supplier || shipment.customer}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={cn("text-xs", getStatusColor(shipment.status))}
                      >
                        {formatStatus(shipment.status)}
                      </Badge>
                    </TableCell>
                    <TableCell>{shipment.items}</TableCell>
                    <TableCell>{shipment.date}</TableCell>
                    <TableCell>
                      {shipment.estimatedDelivery || "-"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        </main>
      </div>
  )
}
