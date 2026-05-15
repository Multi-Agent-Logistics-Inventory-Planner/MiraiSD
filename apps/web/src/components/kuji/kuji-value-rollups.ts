import type { KujiBox, KujiBoxTier } from "@/types/api";

export interface KujiValueRollups {
  valueInBox: number;
  valueHeld: number;
  valueDrawn: number;
  valueOriginal: number;
  evPerDraw: number | null;
  prizesOriginal: number;
  totalActive: number;
  totalHeld: number;
  totalDrawn: number;
}

const ZERO: KujiValueRollups = {
  valueInBox: 0,
  valueHeld: 0,
  valueDrawn: 0,
  valueOriginal: 0,
  evPerDraw: null,
  prizesOriginal: 0,
  totalActive: 0,
  totalHeld: 0,
  totalDrawn: 0,
};

export function effectivePrice(tier: KujiBoxTier): number | null {
  if (tier.price != null) return tier.price;
  if (tier.linkedProductPrice != null) return tier.linkedProductPrice;
  return null;
}

export function computeBoxValues(box: KujiBox | null | undefined): KujiValueRollups {
  if (!box || !box.tiers) return ZERO;

  let valueInBox = 0;
  let valueHeld = 0;
  let valueDrawn = 0;
  let valueOriginal = 0;
  let prizesOriginal = 0;
  let totalActive = 0;
  let totalHeld = 0;
  let totalDrawn = 0;

  for (const t of box.tiers) {
    const active = t.activeCount ?? 0;
    const held = t.inactiveCount ?? 0;
    const drawn = t.drawnCount ?? 0;
    const price = effectivePrice(t);

    totalActive += active;
    totalHeld += held;
    totalDrawn += drawn;
    prizesOriginal += active + held + drawn;

    if (price != null) {
      valueInBox += active * price;
      valueHeld += held * price;
      valueDrawn += drawn * price;
      valueOriginal += (active + held + drawn) * price;
    }
  }

  const evPerDraw = totalActive > 0 ? valueInBox / totalActive : null;

  return {
    valueInBox,
    valueHeld,
    valueDrawn,
    valueOriginal,
    evPerDraw,
    prizesOriginal,
    totalActive,
    totalHeld,
    totalDrawn,
  };
}
