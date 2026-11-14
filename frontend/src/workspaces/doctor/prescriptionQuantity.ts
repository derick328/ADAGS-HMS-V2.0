export const dosageForms = ['CAPSULE', 'TABLET', 'CAPLET', 'SYRUP', 'INJECTION', 'CREAM', 'GEL', 'LOTION', 'PASTE'] as const;

export function dosageForm(form?: string | null) {
  let normalized = form?.trim().toUpperCase();
  if (normalized === 'ORAL SUSPENSION' || normalized === 'SUSPENSION') return 'SYRUP';
  if (normalized?.endsWith('S')) normalized = normalized.slice(0, -1);
  return dosageForms.find(value => value === normalized);
}

export function isAutomaticForm(form?: string | null) {
  return ['CAPSULE', 'TABLET', 'CAPLET'].includes(dosageForm(form) ?? '');
}

export function dosageFormLabel(form?: string | null) {
  const value = dosageForm(form);
  return value ? value[0] + value.slice(1).toLowerCase() : form ?? 'Unspecified';
}

export function automaticSupply(dose: number, frequency: string, days: number): number | null {
  if (!Number.isSafeInteger(dose) || dose <= 0) return null;
  if (frequency === 'STAT') return dose;
  const perDay: Record<string, number> = { OD: 1, BD: 2, TDS: 3, QDS: 4 };
  if (!perDay[frequency] || !Number.isSafeInteger(days) || days <= 0) return null;
  const total = dose * perDay[frequency] * days;
  return Number.isSafeInteger(total) ? total : null;
}
