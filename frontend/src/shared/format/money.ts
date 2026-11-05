// Tanzanian shilling convention: "12,000/=" — no decimals, thousands
// separated. Money never arrives divided; format the minor-unit integer
// directly with Intl.NumberFormat, never by hand.
export function formatMoney(amountMinor: number | undefined | null, currencyCode: string | undefined | null): string {
  const amount = amountMinor ?? 0;
  const formatted = new Intl.NumberFormat('en-TZ').format(amount);
  return currencyCode === 'TZS' || !currencyCode ? `${formatted}/=` : `${formatted} ${currencyCode}`;
}

// Chart axes and dense table cells, where "1,250,000/=" is wider than the
// column. Same minor-unit integer in, same "/=" convention out — thresholds
// only, never arithmetic a component does by hand. Below 10,000 the exact
// figure already fits, so it is left alone rather than rounded to "7k".
export function formatMoneyCompact(
  amountMinor: number | undefined | null,
  currencyCode?: string | null,
): string {
  const amount = amountMinor ?? 0;
  const suffix = currencyCode === 'TZS' || !currencyCode ? '/=' : ` ${currencyCode}`;
  const magnitude = Math.abs(amount);
  if (magnitude >= 1_000_000_000) return `${trimZero(amount / 1_000_000_000)}B${suffix}`;
  if (magnitude >= 1_000_000) return `${trimZero(amount / 1_000_000)}M${suffix}`;
  if (magnitude >= 10_000) return `${trimZero(amount / 1_000)}k${suffix}`;
  return formatMoney(amount, currencyCode);
}

function trimZero(value: number): string {
  const rounded = value.toFixed(1);
  return rounded.endsWith('.0') ? rounded.slice(0, -2) : rounded;
}
