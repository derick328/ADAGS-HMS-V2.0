// The server sends UTC; every display is facility-local (Africa/Dar_es_Salaam).
const TIME_ZONE = 'Africa/Dar_es_Salaam';

export function formatDateTime(value: string | undefined | null): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeStyle: 'short', timeZone: TIME_ZONE }).format(
    new Date(value),
  );
}

export function formatDate(value: string | undefined | null): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeZone: TIME_ZONE }).format(new Date(value));
}

export function formatTime(value: string | undefined | null): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', { timeStyle: 'short', timeZone: TIME_ZONE }).format(new Date(value));
}

/** Weekday + date, facility-local — "Monday, 5 Oct 2026". Used where the day of the week matters to the reader, e.g. a patient's prior-visit list. */
export function formatWeekdayDate(value: string | undefined | null): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', {
    weekday: 'long',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: TIME_ZONE,
  }).format(new Date(value));
}

/** Elapsed time since `since` as a short waiting-room label — "8m", "1h 05m". Shared by every queue that shows how long a patient has been waiting. */
export function formatWaitingSince(since: string | undefined | null): string {
  if (!since) return '—';
  const elapsedMs = Date.now() - new Date(since).getTime();
  const minutes = Math.max(0, Math.floor(elapsedMs / 60_000));
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return `${hours}h ${String(remainingMinutes).padStart(2, '0')}m`;
}

/** [start, end) of a given facility-local calendar day (YYYY-MM-DD), as UTC instants. */
export function dayRange(localDate: string): { from: string; to: string } {
  const from = new Date(`${localDate}T00:00:00+03:00`); // Africa/Dar_es_Salaam is a fixed UTC+3, no DST
  const to = new Date(from.getTime() + 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: to.toISOString() };
}

/** Today's date, facility-local, as YYYY-MM-DD — the default value for a date-filtered screen's picker. */
export function todayLocalDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(new Date());
}

/** [start, end) of today in facility-local time, as UTC instants — the bound every "today" query sends the server. */
export function todayRange(): { from: string; to: string } {
  return dayRange(todayLocalDate());
}
