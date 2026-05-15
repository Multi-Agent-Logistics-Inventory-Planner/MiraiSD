const WHOLE = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

const WITH_DECIMALS = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatMoney(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return WHOLE.format(value);
}

export function formatMoneyDecimals(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return WITH_DECIMALS.format(value);
}
