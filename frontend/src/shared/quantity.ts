/** Normalize floating-point calculation noise without rounding an entered physical quantity. */
export function decimalQuantity(value: number): number | null {
  if (!Number.isFinite(value) || Math.abs(value) > 999_999_999_999) return null;
  const normalized = Math.round(value * 1_000_000) / 1_000_000;
  return Math.abs(normalized - value) <= Number.EPSILON * Math.max(1, Math.abs(value)) * 4 ? normalized : null;
}

export function measuredUnit(unit?: string | null): boolean {
  return ['G', 'MG', 'MCG', 'KG', 'ML', 'L'].includes(unit?.trim().toUpperCase() ?? '');
}
