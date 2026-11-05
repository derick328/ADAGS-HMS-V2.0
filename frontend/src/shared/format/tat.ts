export function formatTatDuration(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds)) return 'Not recorded';
  const minutes = Math.round(Math.max(0, seconds) / 60);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  return `${hours} h ${String(minutes % 60).padStart(2, '0')} min`;
}

export function formatTatTimestamp(value: string | undefined, timezone: string): string {
  return value ? new Intl.DateTimeFormat('en-TZ', { timeZone: timezone, dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Not recorded';
}

export function tatElapsedSeconds(start: string | null | undefined, end: string | null | undefined): number | null {
  if (!start || !end) return null;
  const duration = (Date.parse(end) - Date.parse(start)) / 1000;
  return Number.isFinite(duration) && duration >= 0 ? duration : null;
}

export function tatLocalDate(instant: Date, timezone: string): string {
  const parts = new Intl.DateTimeFormat('en', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(instant);
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
}
