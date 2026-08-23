// Tanzanian shilling convention: "12,000/=" — no decimals, thousands
// separated. Money never arrives divided; format the minor-unit integer
// directly with Intl.NumberFormat, never by hand.
export function formatMoney(amountMinor: number | undefined | null, currencyCode: string | undefined | null): string {
  const amount = amountMinor ?? 0;
  const formatted = new Intl.NumberFormat('en-TZ').format(amount);
  return currencyCode === 'TZS' || !currencyCode ? `${formatted}/=` : `${formatted} ${currencyCode}`;
}
