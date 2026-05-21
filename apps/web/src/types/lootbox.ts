export interface LootboxBalance {
  balance: number;
  reviewCredits: number;
  totalAdjustments: number;
  totalSpent: number;
  totalExpired: number;
}

/** Upcoming expirations bucketed by date — drives the "X coins expire on Y" UI. */
export interface WalletBreakdown {
  total: number;
  expiringSoon: Array<{ amount: number; expiresOn: string }>;
  nextExpiryDate: string | null;
}

export interface LootboxPrize {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  tierId: string;
  tierName: string;
  tierColor: string | null;
  active: boolean;
}

export interface LootboxTier {
  id: string;
  name: string;
  probabilityPct: number;
  displayColor: string | null;
  sortOrder: number;
  active: boolean;
  prizes: LootboxPrize[];
}

/**
 * A crate ("Lootbox") groups tiers + prizes. Player-facing catalog returns only crates
 * currently open; admin catalog returns all crates with all tiers/prizes.
 */
export interface Lootbox {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  cost: number;
  startsAt: string | null;
  endsAt: string | null;
  sortOrder: number;
  tiers: LootboxTier[];
}

/**
 * Admin-shaped crate row: includes active flag, forward-compat siteId, and counts.
 * Used by the /admin/crates list and create/edit forms.
 */
export interface LootboxAdmin {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  cost: number;
  startsAt: string | null;
  endsAt: string | null;
  active: boolean;
  siteId: string | null;
  sortOrder: number;
  tierCount: number;
  prizeCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertLootboxRequest {
  name: string;
  description?: string | null;
  imageUrl?: string | null;
  cost?: number;
  startsAt?: string | null;
  endsAt?: string | null;
  active?: boolean;
  siteId?: string | null;
  sortOrder?: number;
}

export interface PlayLootboxRequest {
  crateId: string;
}

export type LootboxPlayStatus = "WON" | "REDEEMED";

export interface LootboxPlay {
  id: string;
  userId: string;
  userName: string;
  prizeId: string;
  prizeName: string;
  prizeDescription: string | null;
  prizeImageUrl: string | null;
  prizeTierName: string;
  cost: number;
  status: LootboxPlayStatus;
  playedAt: string;
  redeemedAt: string | null;
  redeemedByUserId: string | null;
  redeemedByName: string | null;
}

export interface PlayLootboxResponse {
  play: LootboxPlay;
  newBalance: number;
}

export interface RecentLootboxPlay {
  id: string;
  prizeId: string;
  prizeName: string;
  prizeImageUrl: string | null;
  tierName: string;
  tierColor: string | null;
  userDisplay: string;
  playedAt: string;
}

export type CoinHistoryKind = "REVIEW_CREDIT" | "PLAY" | "ADJUSTMENT";

export interface CoinHistoryEntry {
  kind: CoinHistoryKind;
  at: string;
  delta: number;
  label: string;
  refId: string | null;
  /** Whether this earning row has already expired. Null for PLAY rows. */
  expired: boolean | null;
  /** When this earning expires (or expired). Null for PLAY rows. */
  expiresAt: string | null;
}

export interface CoinAdjustment {
  id: string;
  userId: string;
  userName: string;
  delta: number;
  reason: string;
  grantedByUserId: string;
  grantedByName: string;
  createdAt: string;
}

export interface UserCoinProfile {
  userId: string;
  userName: string;
  userEmail: string;
  balance: LootboxBalance;
  plays: LootboxPlay[];
  adjustments: CoinAdjustment[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * `lootboxId` is required at create-time and ignored on patch updates (tiers can't be
 * moved between crates without a dedicated reparent endpoint).
 */
export interface UpsertTierRequest {
  lootboxId?: string;
  name: string;
  probabilityPct: number;
  displayColor?: string | null;
  sortOrder?: number;
  active?: boolean;
}

export interface UpsertPrizeRequest {
  tierId: string;
  name: string;
  description?: string | null;
  imageUrl?: string | null;
  active?: boolean;
}

export interface CoinAdjustmentRequest {
  userId: string;
  delta: number;
  reason: string;
}

/**
 * Singleton coin-economy config (review-to-coin rate + last-changed metadata).
 * `nextFetchHint` is a pre-baked explanation the backend owns so the UI doesn't
 * have to know about the 6 AM batch schedule.
 */
export interface CoinEconomyConfig {
  reviewCoinRate: number;
  updatedAt: string;
  updatedByUserId: string | null;
  updatedByName: string | null;
  nextFetchHint: string;
}

export interface UpdateCoinEconomyConfigRequest {
  reviewCoinRate: number;
}
