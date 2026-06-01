// Category types (dynamic, from database with self-referencing hierarchy)

export interface Category {
  id: string;
  parentId: string | null;
  name: string;
  slug: string;
  displayOrder: number;
  isActive: boolean;
  usesPacks: boolean;
  children: Category[];
  createdAt: string;
  updatedAt: string;
}

export interface CategoryRequest {
  name: string;
  parentId?: string;
  displayOrder?: number;
  usesPacks?: boolean;
}

export enum LocationType {
  BOX_BIN = "BOX_BIN",
  CABINET = "CABINET",
  DOUBLE_CLAW_MACHINE = "DOUBLE_CLAW_MACHINE",
  FOUR_CORNER_MACHINE = "FOUR_CORNER_MACHINE",
  GACHAPON = "GACHAPON",
  KEYCHAIN_MACHINE = "KEYCHAIN_MACHINE",
  PUSHER_MACHINE = "PUSHER_MACHINE",
  RACK = "RACK",
  SHELF = "SHELF",
  SINGLE_CLAW_MACHINE = "SINGLE_CLAW_MACHINE",
  WINDOW = "WINDOW",
  NOT_ASSIGNED = "NOT_ASSIGNED",
}

export const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  [LocationType.BOX_BIN]: "Box Bin",
  [LocationType.CABINET]: "Cabinet",
  [LocationType.DOUBLE_CLAW_MACHINE]: "Double Claw",
  [LocationType.FOUR_CORNER_MACHINE]: "Four Corner",
  [LocationType.GACHAPON]: "Gachapon",
  [LocationType.KEYCHAIN_MACHINE]: "Keychain Machine",
  [LocationType.PUSHER_MACHINE]: "Pusher",
  [LocationType.RACK]: "Rack",
  [LocationType.SHELF]: "Shelf",
  [LocationType.SINGLE_CLAW_MACHINE]: "Single Claw",
  [LocationType.WINDOW]: "Window",
  [LocationType.NOT_ASSIGNED]: "Not Assigned",
};

export enum StockMovementReason {
  INITIAL_STOCK = "INITIAL_STOCK",
  RESTOCK = "RESTOCK",
  SHIPMENT_RECEIPT = "SHIPMENT_RECEIPT",
  SHIPMENT_RECEIPT_REVERSED = "SHIPMENT_RECEIPT_REVERSED",
  SHIPMENT_PARTIAL_RECEIPT = "SHIPMENT_PARTIAL_RECEIPT",
  SHIPMENT_EDITED = "SHIPMENT_EDITED",
  SHIPMENT_DELETED = "SHIPMENT_DELETED",
  SHIPMENT_STATUS_OVERRIDDEN = "SHIPMENT_STATUS_OVERRIDDEN",
  SALE = "SALE",
  DAMAGE = "DAMAGE",
  ADJUSTMENT = "ADJUSTMENT",
  RETURN = "RETURN",
  TRANSFER = "TRANSFER",
  REMOVED = "REMOVED",
  DISPLAY_SET = "DISPLAY_SET",
  DISPLAY_REMOVED = "DISPLAY_REMOVED",
  DISPLAY_SWAP = "DISPLAY_SWAP",
  KUJI_PRIZE_WON = "KUJI_PRIZE_WON",
  KUJI_DRAW_REVERSED = "KUJI_DRAW_REVERSED",
  KUJI_SLIP_ADJUSTMENT = "KUJI_SLIP_ADJUSTMENT",
}

export enum ShipmentStatus {
  PENDING = "PENDING",
  RECEIVED = "RECEIVED",
  CANCELLED = "CANCELLED",
}

export enum CarrierStatus {
  PRE_TRANSIT = "PRE_TRANSIT",
  IN_TRANSIT = "IN_TRANSIT",
  DELIVERED = "DELIVERED",
  FAILED = "FAILED",
}

export const SHIPMENT_STATUS_LABELS: Record<ShipmentStatus, string> = {
  [ShipmentStatus.PENDING]: "Pending",
  [ShipmentStatus.RECEIVED]: "Received",
  [ShipmentStatus.CANCELLED]: "Cancelled",
};

export const CARRIER_STATUS_LABELS: Record<CarrierStatus, string> = {
  [CarrierStatus.PRE_TRANSIT]: "Pre-transit",
  [CarrierStatus.IN_TRANSIT]: "In transit",
  [CarrierStatus.DELIVERED]: "Delivered",
  [CarrierStatus.FAILED]: "Failed",
};

export const SHIPMENT_STATUS_VARIANTS: Record<
  ShipmentStatus,
  "default" | "secondary" | "destructive" | "outline" | "warning"
> = {
  [ShipmentStatus.PENDING]: "outline",
  [ShipmentStatus.RECEIVED]: "default",
  [ShipmentStatus.CANCELLED]: "destructive",
};

export enum UserRole {
  ADMIN = "ADMIN",
  ASSISTANT_MANAGER = "ASSISTANT_MANAGER",
  EMPLOYEE = "EMPLOYEE",
}

export enum NotificationType {
  LOW_STOCK = "LOW_STOCK",
  OUT_OF_STOCK = "OUT_OF_STOCK",
  REORDER_SUGGESTION = "REORDER_SUGGESTION",
  EXPIRY_WARNING = "EXPIRY_WARNING",
  SYSTEM_ALERT = "SYSTEM_ALERT",
  SHIPMENT_COMPLETED = "SHIPMENT_COMPLETED",
  SHIPMENT_DAMAGED = "SHIPMENT_DAMAGED",
  SHIPMENT_DELIVERY_FAILED = "SHIPMENT_DELIVERY_FAILED",
  DISPLAY_STALE = "DISPLAY_STALE",
  KUJI_PRIZE_DRAWN = "KUJI_PRIZE_DRAWN",
  KUJI_PRIZE_DRAW_UNDONE = "KUJI_PRIZE_DRAW_UNDONE",
}

export enum KujiType {
  PREMADE = "PREMADE",
  CUSTOM = "CUSTOM",
}

export enum KujiBoxStatus {
  OPEN = "OPEN",
  CLOSED = "CLOSED",
}

export enum NotificationSeverity {
  INFO = "INFO",
  WARNING = "WARNING",
  CRITICAL = "CRITICAL",
}

// Auth types

export interface AuthValidationResponse {
  valid: boolean;
  role?: UserRole;
  personId?: string;
  personName?: string;
  message?: string;
}

// Supplier types

export interface Supplier {
  id: string;
  displayName: string;
  canonicalName: string;
  contactEmail?: string;
  isActive: boolean;
  shipmentCount?: number;
  productCount?: number;
  avgLeadTimeDays?: number;
  sigmaL?: number;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierRequest {
  displayName: string;
  contactEmail?: string;
  isActive?: boolean;
}

// Product types

export interface ProductSummary {
  id: string;
  sku?: string | null;
  name: string;
  letter?: string | null;
  templateQuantity?: number | null;
  category: Category;
  imageUrl?: string;
  isActive: boolean;
  quantity: number;
  parentId?: string | null;
  hasChildren?: boolean;
  kujiType?: KujiType | null;
  kujiSlackWebhookUrl?: string | null;
  /** Packs per sealed box for TCG products. NULL when not box-packaged. */
  packsPerBox?: number | null;
}

/**
 * Slim Product DTO returned by list endpoints (GET /api/products, /{id}/children).
 * Carries everything the list views read; drops detail-only fields
 * (description, notes, parentName, parentSku, children, totalChildStock, createdAt).
 * For full details fetch a single product via GET /api/products/{id}.
 */
export interface ProductListItem {
  id: string;
  sku?: string | null;
  name: string;
  imageUrl?: string;
  isActive: boolean;
  quantity: number;
  letter?: string | null;
  templateQuantity?: number | null;
  /** Packs per sealed box for TCG products. NULL when not box-packaged. */
  packsPerBox?: number | null;
  parentId?: string | null;
  kujiType?: KujiType | null;
  kujiSlackWebhookUrl?: string | null;
  reorderPoint?: number;
  targetStockLevel?: number;
  leadTimeDays?: number;
  unitCost?: number;
  msrp?: number;
  preferredSupplierId?: string | null;
  preferredSupplierName?: string | null;
  preferredSupplierAuto?: boolean;
  category: Category;
  hasChildren?: boolean;
  updatedAt: string;
}

export interface Product {
  id: string;
  sku?: string | null;
  letter?: string | null;
  templateQuantity?: number | null;
  category: Category;
  // Parent-child relationship fields
  parentId?: string | null;
  parentName?: string | null;
  parentSku?: string | null;
  children?: ProductSummary[];
  totalChildStock?: number;
  hasChildren?: boolean;
  // Preferred supplier fields
  preferredSupplierId?: string | null;
  preferredSupplierName?: string | null;
  preferredSupplierAuto?: boolean;
  // Last delivered supplier for "Use Auto" feature (only on single product fetch)
  lastDeliveredSupplierId?: string | null;
  lastDeliveredSupplierName?: string | null;
  // Core fields
  name: string;
  description?: string;
  reorderPoint?: number;
  targetStockLevel?: number;
  leadTimeDays?: number;
  unitCost?: number;
  msrp?: number;
  isActive: boolean;
  /** When false, the forecasting pipeline skips this product and prediction surfaces hide it. */
  forecastingEnabled?: boolean;
  quantity: number;
  imageUrl?: string;
  notes?: string;
  kujiType?: KujiType | null;
  kujiSlackWebhookUrl?: string | null;
  /** Packs per sealed box for TCG products. NULL when not box-packaged. */
  packsPerBox?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  sku?: string;
  categoryId?: string;
  parentId?: string;
  letter?: string;
  templateQuantity?: number;
  name: string;
  description?: string;
  initialStock?: number;
  reorderPoint?: number;
  targetStockLevel?: number;
  leadTimeDays?: number;
  unitCost?: number;
  msrp?: number;
  isActive?: boolean;
  /** When false, this product is skipped by the forecasting pipeline. */
  forecastingEnabled?: boolean;
  imageUrl?: string;
  notes?: string;
  /** Direct quantity update for prize products (products with a parent) */
  quantity?: number;
  /** Preferred supplier ID for lead time calculations */
  preferredSupplierId?: string;
  /** True if auto-assigned from delivery, false if manually set */
  preferredSupplierAuto?: boolean;
  /** Kuji classification (only valid when parentId is null). */
  kujiType?: KujiType | null;
  /** Slack webhook for kuji event notifications (only valid when kujiType=CUSTOM). */
  kujiSlackWebhookUrl?: string | null;
  /** Packs per sealed box. Only set for products in the TCG category tree. */
  packsPerBox?: number | null;
}

// User types

export interface User {
  id: string;
  fullName: string;
  email: string;
  role: UserRole;
  canonicalName?: string;
  nameVariants?: string[];
  isReviewTracked?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserRequest {
  fullName: string;
  email: string;
  role: UserRole;
}

export interface UserReviewTrackingRequest {
  canonicalName?: string;
  nameVariants?: string[];
  isReviewTracked?: boolean;
}

export interface Invitation {
  id: string;
  email: string;
  role: string;
  invitedByEmail: string;
  invitedAt: string;
  acceptedAt: string | null;
  status: string;
}

// Unified Location type (replaces 10 type-specific location types)

export interface Location {
  id: string;
  locationCode: string;
  storageLocationId: string;
  storageLocationType: string;
  createdAt: string;
  updatedAt: string;
}

// Unified request for creating/updating locations
export interface LocationRequest {
  locationCode: string;
  storageLocationId: string;
}

// Inventory item embedded in response (simplified product)
export interface InventoryItem {
  id: string;
  sku?: string | null;
  name: string;
  category: Category;
  imageUrl?: string;
  /** Set for child/prize products; null for root products. Used to hide prizes from storage. */
  parentId?: string | null;
  /** Prize letter (e.g. "A") when item is a child/prize. */
  letter?: string | null;
  /** Quantity per kuji set for prize products. */
  templateQuantity?: number | null;
  /** Packs per sealed box for TCG products. NULL when not box-packaged. */
  packsPerBox?: number | null;
}

// Unified Location Inventory type (replaces 9 type-specific inventory types)
export interface LocationInventory {
  id: string;
  locationId: string;
  locationCode: string;
  storageLocationType: string;
  item: InventoryItem;
  quantity: number;
  createdAt: string;
  updatedAt: string;
}

// Inventory request
export interface InventoryRequest {
  itemId: string;
  quantity: number;
  actorId?: string;
  /**
   * Optional reason for creating inventory.
   * Use "RESTOCK" when adding inventory via the adjust dialog.
   * Defaults to "INITIAL_STOCK" if not provided.
   */
  reason?: StockMovementReason;
  /** Optional intake unit ("box") preserved for audit-log readability. */
  intakeUnit?: "pack" | "box";
  /** Raw quantity in the user's chosen unit (paired with intakeUnit). */
  intakeQty?: number;
}

// Shipment types

export interface ShipmentItemAllocation {
  id: string;
  locationType: LocationType;
  locationId?: string;
  locationCode?: string;
  quantity: number;
  receivedAt: string;
}

export interface ShipmentItem {
  id: string;
  item: InventoryItem;
  orderedQuantity: number;
  receivedQuantity: number;
  damagedQuantity: number;
  displayQuantity: number;
  shopQuantity: number;
  unitCost?: number;
  destinationLocationType?: LocationType;
  destinationLocationId?: string;
  allocations?: ShipmentItemAllocation[];
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Shipment {
  id: string;
  shipmentNumber: string;
  supplierId?: string;
  supplierName?: string;
  status: ShipmentStatus;
  carrierStatus?: CarrierStatus | null;
  carrierDeliveredAt?: string | null;
  orderDate: string;
  expectedDeliveryDate?: string;
  actualDeliveryDate?: string;
  totalCost?: number;
  notes?: string;
  trackingId?: string;
  createdBy?: User;
  receivedBy?: User;
  items: ShipmentItem[];
  createdAt: string;
  updatedAt: string;
}

export interface ShipmentItemRequest {
  itemId: string;
  orderedQuantity: number;
  unitCost?: number;
  destinationLocationType?: LocationType;
  destinationLocationId?: string;
  notes?: string;
}

export interface ShipmentRequest {
  shipmentNumber: string;
  supplierId?: string;
  supplierName?: string;
  status?: ShipmentStatus;
  orderDate: string;
  expectedDeliveryDate?: string;
  totalCost?: number;
  notes?: string;
  trackingId?: string;
  createdBy?: string;
  items: ShipmentItemRequest[];
}

export interface DestinationAllocation {
  locationType: LocationType;
  locationId?: string;
  quantity: number;
  /** Optional intake unit ("box") preserved into resulting StockMovement metadata. */
  intakeUnit?: "pack" | "box";
  /** Raw quantity in the user's chosen unit. */
  intakeQty?: number;
}

export interface ShipmentItemReceipt {
  shipmentItemId: string;
  // New: multi-destination allocations
  allocations?: DestinationAllocation[];
  // Items not added to inventory
  damagedQuantity?: number;
  displayQuantity?: number;
  shopQuantity?: number;
  // Legacy fields for backward compatibility
  receivedQuantity?: number;
  destinationLocationType?: LocationType;
  destinationLocationId?: string;
}

export interface ReceiveShipmentRequest {
  actualDeliveryDate: string;
  receivedBy: string;
  itemReceipts: ShipmentItemReceipt[];
}

// Stock Movement types

export interface StockMovementMetadata {
  inventory_id?: string;
  notes?: string;
  transfer?: boolean;
  shipment_receipt?: boolean;
  /** Set to "box" when the user typed quantity in box units; preserves original intent for audit log display. */
  intake_unit?: "box";
  /** Number of boxes the user typed (paired with intake_unit). */
  intake_qty?: number;
}

export interface StockMovement {
  id: number;
  locationType: LocationType;
  item?: InventoryItem;
  itemId?: string;
  fromLocationId?: string;
  toLocationId?: string;
  quantityChange: number;
  reason: StockMovementReason;
  actorId?: string;
  at: string;
  metadata?: StockMovementMetadata;
}

export interface AuditLogEntry {
  id: number;
  locationType: LocationType;
  itemId: string;
  itemSku?: string | null;
  itemName: string;
  fromLocationId?: string;
  fromLocationCode?: string;
  toLocationId?: string;
  toLocationCode?: string;
  previousQuantity?: number;
  currentQuantity?: number;
  quantityChange: number;
  reason: StockMovementReason;
  actorId?: string;
  actorName?: string;
  at: string;
}

export interface AuditLogFilters {
  search?: string;
  actorId?: string;
  reason?: StockMovementReason;
  /** Multi-reason filter; takes precedence over `reason` server-side when non-empty. */
  reasons?: StockMovementReason[];
  fromDate?: string;
  toDate?: string;
  productId?: string;
  locationId?: string;
}

// Field-change entries inside audit_logs.field_changes for SHIPMENT_EDITED / SHIPMENT_DELETED
export interface AuditLogFieldChange {
  field: string;
  from?: unknown;
  to?: unknown;
  changed?: boolean;
  value?: unknown;
}

// New audit log types (grouped actions)
export interface AuditLog {
  id: string;
  actorId?: string;
  actorName?: string;
  reason: StockMovementReason;
  primaryFromLocationCode?: string;
  primaryToLocationCode?: string;
  itemCount: number;
  totalQuantityMoved: number;
  notes?: string;
  createdAt: string;
  productSummary: string;
  shipmentId?: string;
  shipmentNumber?: string;
  fieldChanges?: AuditLogFieldChange[];
  previousStatus?: string;
  newStatus?: string;
  overrideReason?: string;
  reversed: boolean;
}

export interface AuditLogMovement {
  id: number;
  itemId: string;
  itemSku?: string | null;
  itemName: string;
  itemImageUrl?: string;
  fromLocationCode?: string;
  toLocationCode?: string;
  previousQuantity?: number;
  currentQuantity?: number;
  quantityChange: number;
  /** Original intake metadata when the user typed in box units. Used to render "+2 boxes (72 packs)". */
  metadata?: StockMovementMetadata;
}

export interface AuditLogDetail {
  id: string;
  actorId?: string;
  actorName?: string;
  reason: StockMovementReason;
  primaryFromLocationCode?: string;
  primaryToLocationCode?: string;
  itemCount: number;
  totalQuantityMoved: number;
  productSummary?: string;
  notes?: string;
  createdAt: string;
  shipmentId?: string;
  shipmentNumber?: string;
  fieldChanges?: AuditLogFieldChange[];
  previousStatus?: string;
  newStatus?: string;
  overrideReason?: string;
  movements: AuditLogMovement[];
}

export interface BatchAdjustLine {
  inventoryId: string;
  /** Signed quantity change. Sign must agree across all lines in a batch. */
  quantityChange: number;
  /** Optional intake unit ("pack" or "box"). When "box", audit log displays "+N boxes (M packs)". */
  intakeUnit?: "pack" | "box";
  /** Raw quantity in the user's chosen unit (paired with intakeUnit). */
  intakeQty?: number;
}

export interface BatchAdjustStockRequest {
  locationType: LocationType;
  locationId: string;
  adjustments: BatchAdjustLine[];
  reason: StockMovementReason;
  actorId?: string;
  notes?: string;
}

export interface TransferStockRequest {
  sourceLocationType: LocationType;
  sourceInventoryId: string;
  destinationLocationType: LocationType;
  destinationInventoryId?: string;
  destinationLocationId?: string;
  quantity: number;
  actorId: string;
  notes?: string;
}

export interface BatchTransferStockRequest {
  transfers: TransferStockRequest[];
}

// Forecast types
export interface ForecastPrediction {
  id: string;
  itemId: string;
  itemName: string;
  itemSku?: string | null;
  currentStock: number;
  horizonDays: number;
  avgDailyDelta: number;
  daysToStockout: number | null;
  suggestedReorderQty: number;
  suggestedOrderDate: string | null;
  unitCost?: number;
  confidence: number;
  computedAt: string;
}

// Analytics types
export interface CategoryInventory {
  category: string;
  totalItems: number;
  totalStock: number;
}

export interface PerformanceMetrics {
  turnoverRate: number;
  forecastAccuracy: number;
  stockoutRate: number;
  fillRate: number;
}

// Sales Summary types
export interface MonthlySales {
  month: string;
  totalRevenue: number;
  totalCost: number;
  totalProfit: number;
  totalUnits: number;
}

export interface DailySales {
  date: string;
  totalUnits: number;
  totalRevenue: number;
  totalCost: number;
  totalProfit: number;
}

export interface SalesSummary {
  monthlySales: MonthlySales[];
  dailySales: DailySales[];
  totalRevenue: number;
  totalCost: number;
  totalProfit: number;
  totalUnits: number;
  periodStart: string;
  periodEnd: string;
}

// Pagination types

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// Notification types

export interface Notification {
  id: string;
  type: NotificationType;
  severity: NotificationSeverity;
  message: string;
  recipientId?: string;
  itemId?: string;
  itemName?: string;
  inventoryId?: string;
  via?: string[];
  metadata?: Record<string, unknown>;
  createdAt: string;
  deliveredAt?: string;
  resolvedAt?: string;
  readAt?: string;
}

export interface NotificationCounts {
  active: number;
  resolved: number;
  unread: number;
}

export interface NotificationSearchParams {
  search?: string;
  type?: NotificationType;
  resolved?: boolean;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

// API Error types

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

// Helper type for location code patterns
export const LOCATION_CODE_PATTERNS: Record<LocationType, RegExp> = {
  [LocationType.BOX_BIN]: /^B\d+$/,
  [LocationType.CABINET]: /^C\d+$/,
  [LocationType.DOUBLE_CLAW_MACHINE]: /^D\d+$/,
  [LocationType.FOUR_CORNER_MACHINE]: /^M\d+$/,
  [LocationType.GACHAPON]: /^G\d+$/,
  [LocationType.KEYCHAIN_MACHINE]: /^K\d+$/,
  [LocationType.PUSHER_MACHINE]: /^P\d+$/,
  [LocationType.RACK]: /^R\d+$/,
  [LocationType.SHELF]: /^SH\d+$/,
  [LocationType.SINGLE_CLAW_MACHINE]: /^S\d+$/,
  [LocationType.WINDOW]: /^W\d+$/,
  [LocationType.NOT_ASSIGNED]: /^$/,  // No pattern for NOT_ASSIGNED
};

// Maps LocationType to storage_locations.code values in the database
export const STORAGE_LOCATION_CODES: Record<LocationType, string> = {
  [LocationType.BOX_BIN]: "BOX_BINS",
  [LocationType.CABINET]: "CABINETS",
  [LocationType.DOUBLE_CLAW_MACHINE]: "DOUBLE_CLAW",
  [LocationType.FOUR_CORNER_MACHINE]: "FOUR_CORNER",
  [LocationType.GACHAPON]: "GACHAPON",
  [LocationType.KEYCHAIN_MACHINE]: "KEYCHAIN",
  [LocationType.PUSHER_MACHINE]: "PUSHER",
  [LocationType.RACK]: "RACKS",
  [LocationType.SHELF]: "SHELVES",
  [LocationType.SINGLE_CLAW_MACHINE]: "SINGLE_CLAW",
  [LocationType.WINDOW]: "WINDOWS",
  [LocationType.NOT_ASSIGNED]: "NOT_ASSIGNED",
};

// Legacy alias - prefer STORAGE_LOCATION_CODES for new code
export const LOCATION_ENDPOINTS = STORAGE_LOCATION_CODES;

// Location types that only support machine display (no inventory storage)
export const DISPLAY_ONLY_LOCATION_TYPES: LocationType[] = [
  LocationType.GACHAPON,
  LocationType.KEYCHAIN_MACHINE,
];

// Machine location types (machines that display products)
export const MACHINE_LOCATION_TYPES: LocationType[] = [
  LocationType.SINGLE_CLAW_MACHINE,
  LocationType.DOUBLE_CLAW_MACHINE,
  LocationType.FOUR_CORNER_MACHINE,
  LocationType.GACHAPON,
  LocationType.KEYCHAIN_MACHINE,
  LocationType.PUSHER_MACHINE,
];

// Review types

export interface ReviewSummary {
  userId: string;
  userName: string;
  totalReviews: number;
  averageReviewsPerDay: number;
  lastReviewDate: string | null;
}

export interface Review {
  id: string;
  externalId: string;
  userId: string;
  userName: string;
  reviewDate: string;
  reviewText: string;
  rating: number;
  reviewerName: string;
  createdAt: string;
}

export interface UserReviewStats {
  userId: string;
  userName: string;
  allTimeReviewCount: number;
  firstReviewDate: string | null;
  lastReviewDate: string | null;
  selectedMonthReviewCount: number;
  /** Total reviews across all users in the selected month (null if no month selected). */
  selectedMonthTotalReviews: number | null;
  /** This user's share of reviews in the selected month, 0–100 (null if no month or no reviews). */
  selectedMonthPercentage: number | null;
  allTimeRank: number;
}

// Optimized aggregate types for reducing N+1 queries

/**
 * Location with inventory counts from the batch endpoint.
 * Replaces the N+1 pattern of fetching counts per location.
 */
export interface LocationWithCounts {
  id: string;
  locationType: LocationType;
  locationCode: string;
  inventoryRecords: number;
  totalQuantity: number;
  activeDisplayCount: number;
  hasActiveDisplay: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Single inventory entry for a product at a specific location.
 */
export interface ProductInventoryEntry {
  inventoryId: string;
  locationType: LocationType;
  locationId: string | null;
  locationCode: string;
  locationLabel: string;
  quantity: number;
  updatedAt: string;
}

/**
 * All inventory entries for a product across all locations.
 * Replaces the N+1 pattern of querying each location type.
 */
export interface ProductInventoryResponse {
  productId: string;
  productSku: string;
  productName: string;
  totalQuantity: number;
  entries: ProductInventoryEntry[];
}

/**
 * Aggregated inventory total for a single product.
 * Used by dashboard to get totals without N+1 queries.
 */
export interface InventoryTotal {
  itemId: string;
  sku?: string | null;
  name: string;
  imageUrl: string | null;
  categoryId: string | null;
  categoryName: string | null;
  parentCategoryId: string | null;
  parentCategoryName: string | null;
  unitCost: number | null;
  isActive: boolean;
  totalQuantity: number;
  lastUpdatedAt: string | null;
}

// Machine Display types

export interface MachineDisplay {
  id: string;
  locationType: LocationType;
  machineId: string;
  machineCode: string;
  productId: string;
  productName: string;
  productSku?: string | null;
  startedAt: string;
  endedAt: string | null;
  actorId: string | null;
  actorName: string | null;
  daysActive: number;
  stale: boolean;
}

export interface SetMachineDisplayRequest {
  locationType: LocationType;
  machineId: string;
  productId: string;
  actorId?: string;
}

export interface SetMachineDisplayBatchRequest {
  locationType: LocationType;
  machineId: string;
  productIds: string[];
  actorId?: string;
}

export interface MachineDisplayFilters {
  locationType?: LocationType;
  staleOnly?: boolean;
  thresholdDays?: number;
}

// Kuji Box types

export interface KujiBoxTier {
  id: string;
  label: string;
  letter?: string | null;
  linkedProductId?: string | null;
  linkedProductName?: string | null;
  linkedProductImageUrl?: string | null;
  /** Packs-per-box of the linked product (for box/pack toggle on transfer-in). NULL when n/a. */
  linkedProductPacksPerBox?: number | null;
  /** Slips currently winnable (in a slip in the box). */
  activeCount: number;
  /** Slips held back from the pool (kuji-internal). */
  inactiveCount: number;
  /** Cumulative slips drawn since the box was opened. */
  drawnCount: number;
  /** Convenience: activeCount + inactiveCount. */
  totalCount: number;
  price?: number | null;
  /** Linked product's MSRP, used as a fallback when tier.price is null. */
  linkedProductPrice?: number | null;
  /** True when the linked product was created inline at open-box for this tier. */
  autoCreatedProduct?: boolean | null;
}

export interface KujiDailyPayoutPoint {
  date: string;
  valueWon: number;
  slipCount: number;
}

export interface KujiDailyPayoutsResponse {
  boxId: string;
  from: string;
  to: string;
  tz: string;
  series: KujiDailyPayoutPoint[];
  total: { valueWon: number; slipCount: number };
}

export interface KujiBox {
  id: string;
  productId: string;
  productName: string;
  locationId: string;
  locationCode?: string | null;
  locationName?: string | null;
  machineDisplayId?: string | null;
  status: KujiBoxStatus;
  label?: string | null;
  notes?: string | null;
  openedAt: string;
  openedBy?: string | null;
  openedByName?: string | null;
  closedAt?: string | null;
  closedBy?: string | null;
  closedByName?: string | null;
  createdAt: string;
  updatedAt: string;
  tiers: KujiBoxTier[];
  totalCount: number;
}

export interface KujiAllocationByLocation {
  boxId: string;
  boxLabel?: string | null;
  tierId: string;
  tierLabel: string;
  tierLetter?: string | null;
  linkedProductId: string;
  linkedProductName: string;
  count: number;
  machineDisplayId?: string | null;
  /** Location code of the machine the box is on display at, when applicable. */
  machineCode?: string | null;
}

export interface KujiAllocationByProduct {
  boxId: string;
  boxLabel?: string | null;
  locationId: string;
  locationCode: string;
  machineDisplayId?: string | null;
  machineCode?: string | null;
  tierId: string;
  tierLabel: string;
  tierLetter?: string | null;
  count: number;
}

export interface AddKujiTierRequest {
  actorId: string;
  label: string;
  letter?: string | null;
  linkedProductId?: string | null;
  sourceLocationId?: string | null;
  activeCount: number;
  inactiveCount?: number | null;
  price?: number | null;
  autoCreate?: boolean | null;
  productName?: string | null;
  productImageUrl?: string | null;
  productMsrp?: number | null;
}

export interface NewKujiBoxTier {
  label: string;
  letter?: string | null;
  /** Existing product to link. Mutually exclusive with autoCreate. */
  linkedProductId?: string | null;
  /** Required when linkedProductId is set. */
  sourceLocationId?: string | null;
  /** Initial active (winnable, in-slip) count. */
  activeCount: number;
  /** Initial inactive (held back) count. Defaults to 0. */
  inactiveCount?: number | null;
  price?: number | null;
  /** When true, server creates a fresh child product under the kuji parent. */
  autoCreate?: boolean | null;
  /** Required when autoCreate is true. */
  productName?: string | null;
  productImageUrl?: string | null;
  productMsrp?: number | null;
}

export interface OpenKujiBoxRequest {
  productId: string;
  locationId: string;
  machineDisplayId?: string | null;
  label?: string | null;
  notes?: string | null;
  tiers: NewKujiBoxTier[];
  actorId: string;
}

export interface CloseKujiBoxTransferOutTarget {
  tierId: string;
  destinationLocationId: string;
}

export interface CloseKujiBoxRequest {
  actorId: string;
  transferOutTargets?: CloseKujiBoxTransferOutTarget[];
}

export interface DrawLine {
  tierId: string;
  quantity: number;
}

export interface RecordDrawRequest {
  actorId: string;
  notes?: string | null;
  draws: DrawLine[];
}

export interface AddSlipRequest {
  actorId: string;
  quantity: number;
  /** When true, added slips go into the inactive bucket. Defaults to false. */
  inactive?: boolean;
}

export type MoveSlipsDirection = "ACTIVATE" | "DEACTIVATE";

export interface MoveSlipsRequest {
  actorId: string;
  quantity: number;
  direction: MoveSlipsDirection;
}

export interface DeletePrizeRequest {
  actorId: string;
  /** When true (default), decrement activeCount. When false, decrement inactiveCount. */
  fromActive?: boolean;
}

export interface TransferInMoreRequest {
  actorId: string;
  /** Null when the tier's linked product is auto-created — backend mints in place. */
  sourceLocationId: string | null;
  quantity: number;
  /** Optional intake unit ("box") preserved on resulting kuji TRANSFER stock movements. */
  intakeUnit?: "pack" | "box";
  /** Raw quantity in the user's chosen unit. */
  intakeQty?: number;
}

export interface PatchKujiTierRequest {
  actorId: string;
  label?: string | null;
  letter?: string | null;
  clearLetter?: boolean | null;
  linkedProductId?: string | null;
  clearLinkedProduct?: boolean | null;
  linkedProductDestinationLocationId?: string | null;
  activeCount?: number | null;
  inactiveCount?: number | null;
  /** Source location when bringing in counts of a newly-linked product in the same patch. */
  newProductSourceLocationId?: string | null;
  /** Units of the new linked product to add to active in the same patch. */
  newProductActiveCount?: number | null;
  /** Units of the new linked product to add to inactive in the same patch. */
  newProductInactiveCount?: number | null;
  price?: number | null;
  clearPrice?: boolean | null;
}
