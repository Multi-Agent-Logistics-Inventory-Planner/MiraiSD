// Enums matching backend

export enum ProductCategory {
  PLUSHIE = "PLUSHIE",
  KEYCHAIN = "KEYCHAIN",
  FIGURINE = "FIGURINE",
  GACHAPON = "GACHAPON",
  BLIND_BOX = "BLIND_BOX",
  BUILD_KIT = "BUILD_KIT",
  GUNDAM = "GUNDAM",
  KUJI = "KUJI",
  MISCELLANEOUS = "MISCELLANEOUS",
}

export enum ProductSubcategory {
  DREAMS = "DREAMS",
  POKEMON = "POKEMON",
  POPMART = "POPMART",
  SANRIO_SAN_X = "SANRIO_SAN_X",
  FIFTY_TWO_TOYS = "FIFTY_TWO_TOYS",
  ROLIFE = "ROLIFE",
  TOY_CITY = "TOY_CITY",
  MINISO = "MINISO",
  MISCELLANEOUS = "MISCELLANEOUS",
}

// Display labels for enums
export const PRODUCT_CATEGORY_LABELS: Record<ProductCategory, string> = {
  [ProductCategory.PLUSHIE]: "Plushie",
  [ProductCategory.KEYCHAIN]: "Keychain",
  [ProductCategory.FIGURINE]: "Figurine",
  [ProductCategory.GACHAPON]: "Gachapon",
  [ProductCategory.BLIND_BOX]: "Blind Box",
  [ProductCategory.BUILD_KIT]: "Build Kit",
  [ProductCategory.GUNDAM]: "Gundam",
  [ProductCategory.KUJI]: "Kuji",
  [ProductCategory.MISCELLANEOUS]: "Miscellaneous",
};

export const PRODUCT_SUBCATEGORY_LABELS: Record<ProductSubcategory, string> = {
  [ProductSubcategory.DREAMS]: "Dreams",
  [ProductSubcategory.POKEMON]: "Pokemon",
  [ProductSubcategory.POPMART]: "Pop Mart",
  [ProductSubcategory.SANRIO_SAN_X]: "Sanrio / San-X",
  [ProductSubcategory.FIFTY_TWO_TOYS]: "52 Toys",
  [ProductSubcategory.ROLIFE]: "Rolife",
  [ProductSubcategory.TOY_CITY]: "Toy City",
  [ProductSubcategory.MINISO]: "Miniso",
  [ProductSubcategory.MISCELLANEOUS]: "Miscellaneous",
};

export enum LocationType {
  BOX_BIN = "BOX_BIN",
  SINGLE_CLAW_MACHINE = "SINGLE_CLAW_MACHINE",
  DOUBLE_CLAW_MACHINE = "DOUBLE_CLAW_MACHINE",
  KEYCHAIN_MACHINE = "KEYCHAIN_MACHINE",
  CABINET = "CABINET",
  RACK = "RACK",
  NOT_ASSIGNED = "NOT_ASSIGNED",
}

export const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  [LocationType.BOX_BIN]: "Box Bin",
  [LocationType.SINGLE_CLAW_MACHINE]: "Single Claw",
  [LocationType.DOUBLE_CLAW_MACHINE]: "Double Claw",
  [LocationType.KEYCHAIN_MACHINE]: "Keychain Machine",
  [LocationType.CABINET]: "Cabinet",
  [LocationType.RACK]: "Rack",
  [LocationType.NOT_ASSIGNED]: "Not Assigned",
};

export enum StockMovementReason {
  INITIAL_STOCK = "INITIAL_STOCK",
  RESTOCK = "RESTOCK",
  SALE = "SALE",
  DAMAGE = "DAMAGE",
  ADJUSTMENT = "ADJUSTMENT",
  RETURN = "RETURN",
  TRANSFER = "TRANSFER",
}

export enum ShipmentStatus {
  PENDING = "PENDING",
  IN_TRANSIT = "IN_TRANSIT",
  DELIVERED = "DELIVERED",
  CANCELLED = "CANCELLED",
}

export const SHIPMENT_STATUS_LABELS: Record<ShipmentStatus, string> = {
  [ShipmentStatus.PENDING]: "Pending",
  [ShipmentStatus.IN_TRANSIT]: "In Transit",
  [ShipmentStatus.DELIVERED]: "Delivered",
  [ShipmentStatus.CANCELLED]: "Cancelled",
};

export const SHIPMENT_STATUS_VARIANTS: Record<
  ShipmentStatus,
  "default" | "secondary" | "destructive" | "outline"
> = {
  [ShipmentStatus.PENDING]: "outline",
  [ShipmentStatus.IN_TRANSIT]: "secondary",
  [ShipmentStatus.DELIVERED]: "default",
  [ShipmentStatus.CANCELLED]: "destructive",
};

export enum UserRole {
  ADMIN = "ADMIN",
  EMPLOYEE = "EMPLOYEE",
}

export enum NotificationType {
  LOW_STOCK = "LOW_STOCK",
  OUT_OF_STOCK = "OUT_OF_STOCK",
  REORDER_SUGGESTION = "REORDER_SUGGESTION",
  EXPIRY_WARNING = "EXPIRY_WARNING",
  SYSTEM_ALERT = "SYSTEM_ALERT",
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

// Product types

export interface Product {
  id: string;
  sku: string;
  category: ProductCategory;
  subcategory?: ProductSubcategory;
  name: string;
  description?: string;
  reorderPoint?: number;
  targetStockLevel?: number;
  leadTimeDays?: number;
  unitCost?: number;
  isActive: boolean;
  imageUrl?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  sku: string;
  category: ProductCategory;
  subcategory?: ProductSubcategory;
  name: string;
  description?: string;
  reorderPoint?: number;
  targetStockLevel?: number;
  leadTimeDays?: number;
  unitCost?: number;
  isActive?: boolean;
  imageUrl?: string;
  notes?: string;
}

// User types

export interface User {
  id: string;
  fullName: string;
  email: string;
  role: UserRole;
  createdAt: string;
  updatedAt: string;
}

export interface UserRequest {
  fullName: string;
  email: string;
  role: UserRole;
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

// Base Storage Location type

interface BaseLocation {
  id: string;
  createdAt: string;
  updatedAt: string;
}

// Storage Location types

export interface BoxBin extends BaseLocation {
  boxBinCode: string;
}

export interface Rack extends BaseLocation {
  rackCode: string;
}

export interface Cabinet extends BaseLocation {
  cabinetCode: string;
}

export interface SingleClawMachine extends BaseLocation {
  singleClawMachineCode: string;
}

export interface DoubleClawMachine extends BaseLocation {
  doubleClawMachineCode: string;
}

export interface KeychainMachine extends BaseLocation {
  keychainMachineCode: string;
}

// Union type for all locations
export type StorageLocation =
  | BoxBin
  | Rack
  | Cabinet
  | SingleClawMachine
  | DoubleClawMachine
  | KeychainMachine;

// Location request types
export interface BoxBinRequest {
  boxBinCode: string;
}

export interface RackRequest {
  rackCode: string;
}

export interface CabinetRequest {
  cabinetCode: string;
}

export interface SingleClawMachineRequest {
  singleClawMachineCode: string;
}

export interface DoubleClawMachineRequest {
  doubleClawMachineCode: string;
}

export interface KeychainMachineRequest {
  keychainMachineCode: string;
}

// Inventory item embedded in response (simplified product)
export interface InventoryItem {
  id: string;
  sku: string;
  name: string;
  category: ProductCategory;
  subcategory?: ProductSubcategory;
}

// Base Inventory type
interface BaseInventory {
  id: string;
  item: InventoryItem;
  quantity: number;
  createdAt: string;
  updatedAt: string;
}

// Inventory types for each location
export interface BoxBinInventory extends BaseInventory {
  boxBinId: string;
  boxBinCode: string;
}

export interface RackInventory extends BaseInventory {
  rackId: string;
  rackCode: string;
}

export interface CabinetInventory extends BaseInventory {
  cabinetId: string;
  cabinetCode: string;
}

export interface SingleClawMachineInventory extends BaseInventory {
  singleClawMachineId: string;
  singleClawMachineCode: string;
}

export interface DoubleClawMachineInventory extends BaseInventory {
  doubleClawMachineId: string;
  doubleClawMachineCode: string;
}

export interface KeychainMachineInventory extends BaseInventory {
  keychainMachineId: string;
  keychainMachineCode: string;
}

export interface NotAssignedInventory extends BaseInventory {
  // No location fields - NOT_ASSIGNED has no physical location
}

// Union type for all inventory
export type Inventory =
  | BoxBinInventory
  | RackInventory
  | CabinetInventory
  | SingleClawMachineInventory
  | DoubleClawMachineInventory
  | KeychainMachineInventory
  | NotAssignedInventory;

// Inventory request
export interface InventoryRequest {
  itemId: string;
  quantity: number;
}

// Shipment types

export interface ShipmentItem {
  id: string;
  item: InventoryItem;
  orderedQuantity: number;
  receivedQuantity: number;
  unitCost?: number;
  destinationLocationType?: LocationType;
  destinationLocationId?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Shipment {
  id: string;
  shipmentNumber: string;
  supplierName?: string;
  status: ShipmentStatus;
  orderDate: string;
  expectedDeliveryDate?: string;
  actualDeliveryDate?: string;
  totalCost?: number;
  notes?: string;
  createdBy?: User;
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
  supplierName?: string;
  status?: ShipmentStatus;
  orderDate: string;
  expectedDeliveryDate?: string;
  totalCost?: number;
  notes?: string;
  createdBy?: string;
  items: ShipmentItemRequest[];
}

export interface ShipmentItemReceipt {
  shipmentItemId: string;
  receivedQuantity: number;
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
  itemSku: string;
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
  fromDate?: string;
  toDate?: string;
}

export interface AdjustStockRequest {
  quantityChange: number;
  reason: StockMovementReason;
  actorId: string;
  notes?: string;
}

export interface TransferStockRequest {
  sourceLocationType: LocationType;
  sourceInventoryId: string;
  destinationLocationType: LocationType;
  destinationInventoryId: string;
  quantity: number;
  actorId: string;
  notes?: string;
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
  inventoryId?: string;
  via?: string[];
  metadata?: Record<string, unknown>;
  createdAt: string;
  deliveredAt?: string;
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
  [LocationType.RACK]: /^R\d+$/,
  [LocationType.CABINET]: /^C\d+$/,
  [LocationType.SINGLE_CLAW_MACHINE]: /^S\d+$/,
  [LocationType.DOUBLE_CLAW_MACHINE]: /^D\d+$/,
  [LocationType.KEYCHAIN_MACHINE]: /^M\d+$/,
  [LocationType.NOT_ASSIGNED]: /^N\d+$/,
};

// Helper type for location endpoints
export const LOCATION_ENDPOINTS: Record<LocationType, string> = {
  [LocationType.BOX_BIN]: "box-bins",
  [LocationType.RACK]: "racks",
  [LocationType.CABINET]: "cabinets",
  [LocationType.SINGLE_CLAW_MACHINE]: "single-claw-machines",
  [LocationType.DOUBLE_CLAW_MACHINE]: "double-claw-machines",
  [LocationType.KEYCHAIN_MACHINE]: "keychain-machines",
  [LocationType.NOT_ASSIGNED]: "not-assigned",
};
