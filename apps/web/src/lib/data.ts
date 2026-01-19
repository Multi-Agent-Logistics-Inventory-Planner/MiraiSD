// Mock data for the inventory tracker

export type InventoryItem = {
  id: string
  sku: string
  name: string
  category: string
  stock: number
  maxStock: number
  status: "good" | "low" | "critical" | "out-of-stock"
  location: string
  cost: number
  lastUpdated: string
}

export type Shipment = {
  id: string
  shipmentNumber: string
  type: "inbound" | "outbound"
  supplier?: string
  customer?: string
  status: "delivered" | "in-transit" | "delayed" | "pending"
  items: number
  date: string
  estimatedDelivery?: string
}

export type Notification = {
  id: string
  type: "stockout" | "low-stock" | "shipment" | "prediction" | "system"
  severity: "critical" | "warning" | "info"
  message: string
  sku?: string
  timestamp: string
  read: boolean
}

export type User = {
  id: string
  name: string
  email: string
  role: "admin" | "manager" | "employee"
  status: "active" | "inactive" | "pending"
  lastLogin: string
  createdAt: string
}

export type StockPrediction = {
  id: string
  sku: string
  name: string
  currentStock: number
  dailySales: number
  daysUntilStockout: number
  recommendedOrder: number
  confidence: number
}

// Mock inventory data
export const inventoryItems: InventoryItem[] = [
  {
    id: "1",
    sku: "SKU-001",
    name: "Wireless Headphones",
    category: "Electronics",
    stock: 15,
    maxStock: 100,
    status: "low",
    location: "Warehouse A",
    cost: 45.99,
    lastUpdated: "2 hours ago",
  },
  {
    id: "2",
    sku: "SKU-002",
    name: "Bluetooth Speaker",
    category: "Electronics",
    stock: 85,
    maxStock: 100,
    status: "good",
    location: "Warehouse A",
    cost: 29.99,
    lastUpdated: "1 hour ago",
  },
  {
    id: "3",
    sku: "SKU-003",
    name: "USB-C Cable",
    category: "Accessories",
    stock: 0,
    maxStock: 200,
    status: "out-of-stock",
    location: "Warehouse B",
    cost: 9.99,
    lastUpdated: "30 minutes ago",
  },
  {
    id: "4",
    sku: "SKU-004",
    name: "Phone Case",
    category: "Accessories",
    stock: 5,
    maxStock: 150,
    status: "critical",
    location: "Warehouse A",
    cost: 12.99,
    lastUpdated: "4 hours ago",
  },
  {
    id: "5",
    sku: "SKU-005",
    name: "Laptop Stand",
    category: "Office",
    stock: 42,
    maxStock: 50,
    status: "good",
    location: "Warehouse C",
    cost: 34.99,
    lastUpdated: "1 day ago",
  },
  {
    id: "6",
    sku: "SKU-006",
    name: "Wireless Mouse",
    category: "Electronics",
    stock: 28,
    maxStock: 80,
    status: "good",
    location: "Warehouse A",
    cost: 24.99,
    lastUpdated: "3 hours ago",
  },
  {
    id: "7",
    sku: "SKU-007",
    name: "Keyboard",
    category: "Electronics",
    stock: 8,
    maxStock: 60,
    status: "low",
    location: "Warehouse B",
    cost: 59.99,
    lastUpdated: "5 hours ago",
  },
  {
    id: "8",
    sku: "SKU-008",
    name: "Monitor Arm",
    category: "Office",
    stock: 3,
    maxStock: 30,
    status: "critical",
    location: "Warehouse C",
    cost: 89.99,
    lastUpdated: "2 days ago",
  },
]

// Mock shipments data
export const shipments: Shipment[] = [
  {
    id: "1",
    shipmentNumber: "SHP-001",
    type: "inbound",
    supplier: "Tech Supplies Co.",
    status: "in-transit",
    items: 150,
    date: "2026-01-15",
    estimatedDelivery: "2026-01-20",
  },
  {
    id: "2",
    shipmentNumber: "SHP-002",
    type: "outbound",
    customer: "Retail Store ABC",
    status: "delivered",
    items: 45,
    date: "2026-01-10",
  },
  {
    id: "3",
    shipmentNumber: "SHP-003",
    type: "inbound",
    supplier: "Electronics Direct",
    status: "delayed",
    items: 200,
    date: "2026-01-12",
    estimatedDelivery: "2026-01-22",
  },
  {
    id: "4",
    shipmentNumber: "SHP-004",
    type: "outbound",
    customer: "Online Order #4521",
    status: "in-transit",
    items: 12,
    date: "2026-01-18",
  },
  {
    id: "5",
    shipmentNumber: "SHP-005",
    type: "inbound",
    supplier: "Office Essentials",
    status: "pending",
    items: 75,
    date: "2026-01-19",
    estimatedDelivery: "2026-01-25",
  },
]

// Mock notifications data
export const notifications: Notification[] = [
  {
    id: "1",
    type: "stockout",
    severity: "critical",
    message: "USB-C Cable is now out of stock",
    sku: "SKU-003",
    timestamp: "30 minutes ago",
    read: false,
  },
  {
    id: "2",
    type: "low-stock",
    severity: "warning",
    message: "Wireless Headphones stock is low (15 units)",
    sku: "SKU-001",
    timestamp: "2 hours ago",
    read: false,
  },
  {
    id: "3",
    type: "prediction",
    severity: "warning",
    message: "Phone Case predicted to run out in 3 days",
    sku: "SKU-004",
    timestamp: "4 hours ago",
    read: true,
  },
  {
    id: "4",
    type: "shipment",
    severity: "info",
    message: "Shipment SHP-001 is in transit",
    timestamp: "6 hours ago",
    read: true,
  },
  {
    id: "5",
    type: "shipment",
    severity: "critical",
    message: "Shipment SHP-003 has been delayed",
    timestamp: "1 day ago",
    read: false,
  },
]

// Mock users data
export const users: User[] = [
  {
    id: "1",
    name: "John Smith",
    email: "john.smith@company.com",
    role: "admin",
    status: "active",
    lastLogin: "2026-01-19 09:30",
    createdAt: "2025-06-15",
  },
  {
    id: "2",
    name: "Sarah Johnson",
    email: "sarah.j@company.com",
    role: "manager",
    status: "active",
    lastLogin: "2026-01-19 08:45",
    createdAt: "2025-08-20",
  },
  {
    id: "3",
    name: "Mike Wilson",
    email: "mike.w@company.com",
    role: "employee",
    status: "active",
    lastLogin: "2026-01-18 17:00",
    createdAt: "2025-10-01",
  },
  {
    id: "4",
    name: "Emily Davis",
    email: "emily.d@company.com",
    role: "employee",
    status: "inactive",
    lastLogin: "2026-01-10 14:20",
    createdAt: "2025-09-15",
  },
  {
    id: "5",
    name: "David Brown",
    email: "david.b@company.com",
    role: "employee",
    status: "pending",
    lastLogin: "-",
    createdAt: "2026-01-17",
  },
]

// Mock stock predictions data
export const stockPredictions: StockPrediction[] = [
  {
    id: "1",
    sku: "SKU-004",
    name: "Phone Case",
    currentStock: 5,
    dailySales: 2,
    daysUntilStockout: 3,
    recommendedOrder: 100,
    confidence: 92,
  },
  {
    id: "2",
    sku: "SKU-001",
    name: "Wireless Headphones",
    currentStock: 15,
    dailySales: 3,
    daysUntilStockout: 5,
    recommendedOrder: 50,
    confidence: 88,
  },
  {
    id: "3",
    sku: "SKU-008",
    name: "Monitor Arm",
    currentStock: 3,
    dailySales: 1,
    daysUntilStockout: 3,
    recommendedOrder: 20,
    confidence: 85,
  },
  {
    id: "4",
    sku: "SKU-007",
    name: "Keyboard",
    currentStock: 8,
    dailySales: 1,
    daysUntilStockout: 8,
    recommendedOrder: 40,
    confidence: 78,
  },
]

// Dashboard statistics
export const dashboardStats = {
  totalStockValue: 125420.5,
  stockValueChange: 12.5,
  lowStockItems: 2,
  criticalItems: 2,
  outOfStockItems: 1,
  totalSKUs: 8,
}

// Category distribution for pie chart
export const categoryDistribution = [
  { name: "Electronics", value: 136, fill: "var(--chart-1)" },
  { name: "Accessories", value: 5, fill: "var(--chart-2)" },
  { name: "Office", value: 45, fill: "var(--chart-3)" },
]

// Recent activity
export const recentActivity = [
  { id: "1", action: "USB-C Cable is now out of stock", time: "30 minutes ago" },
  { id: "2", action: "Received shipment SHP-002 (45 items)", time: "1 hour ago" },
  { id: "3", action: "Stock adjustment: Wireless Headphones -5 units", time: "2 hours ago" },
  { id: "4", action: "New item added: Laptop Stand", time: "1 day ago" },
  { id: "5", action: "Price update: Bluetooth Speaker", time: "2 days ago" },
]
