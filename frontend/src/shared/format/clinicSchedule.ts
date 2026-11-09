// Clinic visits use the facility's fixed UTC+03:00 time zone.
const TZ_OFFSET_MS = 3 * 60 * 60 * 1000;

function clinicLocalParts(instant: Date) {
  const local = new Date(instant.getTime() + TZ_OFFSET_MS).toISOString();
  return { date: local.slice(0, 10), time: local.slice(11, 16) };
}

export function clinicDateTimeLimits(now = new Date()) {
  const nextMinute = new Date(Math.floor(now.getTime() / 60_000) * 60_000 + 60_000);
  return clinicLocalParts(nextMinute);
}

export function scheduledClinicInstant(date: string, time: string, now = new Date()): string {
  const scheduled = new Date(`${date}T${time}:00+03:00`);
  if (Number.isNaN(scheduled.getTime()) || scheduled.getTime() <= now.getTime()) {
    throw new Error('Choose a future clinic visit date and time.');
  }
  return scheduled.toISOString();
}
