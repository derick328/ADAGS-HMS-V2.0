export const dosageForms = ['CAPSULE', 'TABLET', 'TABLETS', 'ORAL_TABLETS', 'CAPLET', 'SYRUP',
  'SUSPENSION', 'ORAL_SUSPENSION', 'INJECTION', 'CREAM', 'GEL', 'LOTION', 'PASTE'] as const;

export function dosageForm(form?: string | null) {
  let normalized = form?.trim().toUpperCase().replaceAll(' ', '_');
  if (normalized?.endsWith('S') && normalized !== 'TABLETS' && normalized !== 'ORAL_TABLETS') normalized = normalized.slice(0, -1);
  return dosageForms.find(value => value === normalized);
}

export function isAutomaticForm(form?: string | null) {
  return ['CAPSULE', 'TABLET', 'TABLETS', 'ORAL_TABLETS', 'CAPLET'].includes(dosageForm(form) ?? '');
}

export function baseUnitForForm(form?: string | null) {
  const value = dosageForm(form);
  return value === 'TABLETS' || value === 'ORAL_TABLETS' ? 'TABLET' : value;
}

export function isLiquidOralForm(form?: string | null) {
  return ['SYRUP', 'SUSPENSION', 'ORAL_SUSPENSION'].includes(dosageForm(form) ?? '');
}

export function dosageFormLabel(form?: string | null) {
  const value = dosageForm(form);
  return value ? value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) : form ?? 'Unspecified';
}

export function automaticSupply(dose: number, frequency: string, days: number): number | null {
  if (!Number.isSafeInteger(dose) || dose <= 0) return null;
  if (frequency === 'STAT') return dose;
  const perDay: Record<string, number> = { OD: 1, BD: 2, TDS: 3, QDS: 4 };
  if (!perDay[frequency] || !Number.isSafeInteger(days) || days <= 0) return null;
  const total = dose * perDay[frequency] * days;
  return Number.isSafeInteger(total) ? total : null;
}
