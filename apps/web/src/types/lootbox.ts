export interface LootboxBalance {
  balance: number;
  reviewCredits: number;
  totalAdjustments: number;
  totalSpent: number;
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

export type CoinHistoryKind = "REVIEW_CREDIT" | "PLAY" | "ADJUSTMENT";

export interface CoinHistoryEntry {
  kind: CoinHistoryKind;
  at: string;
  delta: number;
  label: string;
  refId: string | null;
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

export interface UpsertTierRequest {
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
