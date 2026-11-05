export type RangePreset = 'TODAY' | 'LAST_7' | 'MTD' | 'LAST_30' | 'YTD' | 'CUSTOM';

export interface DateRange {
  from: string;
  to: string;
}

export const RANGE_PRESETS: { key: Exclude<RangePreset, 'CUSTOM'>; label: string }[] = [
  { key: 'TODAY', label: 'Today' },
  { key: 'LAST_7', label: 'Last 7 days' },
  { key: 'MTD', label: 'This month' },
  { key: 'LAST_30', label: 'Last 30 days' },
  { key: 'YTD', label: 'Year to date' },
];

/**
 * Date presets for the financial reports. Kept out of RangeFilter.tsx so that
 * file exports components only (fast refresh) and so this logic is importable
 * by a page that renders no filter — /admin fixes its own period.
 *
 * Dates are formatted from the LOCAL calendar date, never through
 * toISOString(), which converts to UTC first: at 01:00 in Dar es Salaam
 * (UTC+3) that reports yesterday, so "Today" would silently query the wrong
 * day. The server compares these against `timestamptz::date` in its own
 * session zone, so a local calendar date is the right thing to send.
 */
export function resolveRange(preset: RangePreset, custom: DateRange): DateRange {
  const now = new Date();
  const today = toLocalIsoDate(now);
  switch (preset) {
    case 'TODAY':
      return { from: today, to: today };
    case 'LAST_7':
      return { from: toLocalIsoDate(addDays(now, -6)), to: today };
    case 'MTD':
      return { from: toLocalIsoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: today };
    case 'LAST_30':
      return { from: toLocalIsoDate(addDays(now, -29)), to: today };
    case 'YTD':
      return { from: toLocalIsoDate(new Date(now.getFullYear(), 0, 1)), to: today };
    case 'CUSTOM':
      return custom;
  }
}

export function toLocalIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Mirrors TrendGranularity.forSpan on the server, which picks the buckets. */
export function granularityLabel(range: DateRange): string {
  const from = new Date(`${range.from}T00:00`);
  const to = new Date(`${range.to}T00:00`);
  const days = Math.round((to.getTime() - from.getTime()) / 86_400_000);
  if (days <= 31) return 'Daily';
  if (days <= 182) return 'Weekly';
  return 'Monthly';
}

function addDays(date: Date, days: number): Date {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
}
